package ubus

import Log
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicLong

@SharedImmutable
val idCache = mutableMapOf<String, AtomicInt>()

@SharedImmutable
internal val reqCounter = AtomicLong()

@ThreadLocal
object Invoker {
    fun invoke(ctx: CValuesRef<ubus_context>, obj: String, method: String, payload: String, first: Boolean = true): Long {
        val id = lookup(ctx, obj)
        if (id == 0) {
            Log.debug("invoke err, lookup ${obj} failed")
            return 0
        }

        val scope = Arena()
        val reqId = reqCounter.addAndGet(1)
        val req = cValue<ubus_request> {
            priv = StableRef.create(InvokerRequest(reqId, scope)).asCPointer()

            data_cb = staticCFunction { req, type, msg ->
                initRuntimeIfNeeded()
                memScoped {
                    val invokerRequest = req!!.pointed.priv!!.asStableRef<InvokerRequest>().get()
                    invokerRequest.arena.clear()

                    ubusListener?.onInvokeCallback(true, invokerRequest.id, blobmsg_format_json(msg, true)!!.toKString())
                }
            }
            complete_cb = staticCFunction { req, ret ->
                initRuntimeIfNeeded()
                if (ret != 0) {
                    Log.debug("")
                    memScoped {
                        val invokerRequest = req!!.pointed.priv!!.asStableRef<InvokerRequest>().get()
                        invokerRequest.arena.clear()
                        ubusListener?.onInvokeCallback(false, invokerRequest.id)
                    }
                }
            }
        }.getPointer(scope)

        var invoke = 0
        memScoped {
            val buf = cValue<blob_buf>().getPointer(this)
            blob_buf_init(buf, 0)
            buildUbusParam(payload, buf)

            invoke = ubus_invoke_async(ctx, id.toUInt(), obj, buf[0].head, req)
            if (invoke == 0) {
                ubus_complete_request_async(ctx, req)
            }
        }

        if (invoke != 0) {
            scope.clear()
            Log.debug("invoke err. ${ubus_strerror(invoke)?.toKString()}")

            if (first) {
                return invoke(ctx, obj, method, payload, false)
            }
            return 0
        }

        return reqId
    }

    private fun lookup(ctx: CValuesRef<ubus_context>, name: String, useCache: Boolean = true): Int {
        if (useCache) {
            val cache = idCache[name]
            if (cache != null) {
                return cache.value
            }
        }

        return memScoped {
            val idRef = cValue<UIntVar>().getPointer(this)
            val id = if (ubus_lookup_id(ctx, name, idRef) != 0) {
                0
            } else {
                idRef[0].toInt()
            }
            idCache.getOrPut(name, {
                AtomicInt(0)
            }).value = id
            id
        }

    }
}

data class InvokerRequest(val id: Long, val arena: Arena)