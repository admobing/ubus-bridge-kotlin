package ubus

import Log.debug
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

@SharedImmutable
internal val idCache = AtomicReference<MutableMap<String, UInt>>(hashMapOf<String, UInt>().freeze())

@SharedImmutable
internal val reqCounter = AtomicInt(0)


@ThreadLocal
internal object Invoker {
    fun invoke(ctx: CValuesRef<ubus_context>, obj: String, method: String, payload: String, first: Boolean = true): Long {
        val id = lookup(ctx, obj, first)
        if (id == 0u) {
            debug("invoke err, lookup ${obj} failed")
            return 0
        }

        val reqHold = InvokeReq(reqCounter.addAndGet(1))
        val req = reqHold.scope.alloc<ubus_request> {
            priv = StableRef.create(reqHold).asCPointer()
            
            data_cb = staticCFunction { req, type, msg ->
                initRuntimeIfNeeded()

                val idRef = req!!.pointed.priv!!.asStableRef<InvokeReq>()
                val idReq = idRef.get()
                getUbusListener().onInvokeCallback(true, idReq.id, blobmsg_format_json(msg, true)!!.toKString())
                idReq.clear()
                idRef.dispose()

                debug("data_cb")
            }

            complete_cb = staticCFunction { req, ret ->
                initRuntimeIfNeeded()
                if (ret != 0) {
                    val idRef = req!!.pointed.priv!!.asStableRef<InvokeReq>()
                    val idReq = idRef.get()
                    getUbusListener().onInvokeCallback(false, idReq.id)
                    idReq.clear()
                    idRef.dispose()
                }
                debug("complete cb ${ret}")
            }
        }.ptr

        var invoke = 0
        memScoped {
            val buf = alloc<blob_buf>().ptr
            blob_buf_init(buf, 0)
            if (!blobmsg_add_json_from_string(buf, payload)) {
                error("invoke err. payload parse failed.")
                return 0
            }
//            invoke = ubus_invoke(ctx, id, method, buf.pointed.head, staticCFunction { req, ret, msg ->
//                initRuntimeIfNeeded()
//
//                val idRef = req!!.pointed.priv!!.asStableRef<InvokeReq>()
//                val idReq = idRef.get()
//                getUbusListener().onInvokeCallback(true, idReq.id, blobmsg_format_json(msg, true)!!.toKString())
//                idReq.clear()
//                idRef.dispose()
//
//            }, StableRef.create(reqHold).asCPointer(), 1000)

            invoke = ubus_invoke_async(ctx, id, method, buf.pointed.head, req)
            if (invoke == 0) {
                ubus_complete_request_async(ctx, req)
            }
        }

        if (invoke != 0) {
            debug("invoke err. ${invoke}, ${ubus_strerror(invoke)?.toKString()}")
            val idRef = req!!.pointed.priv!!.asStableRef<InvokeReq>()
            val idReq = idRef.get()
            idReq.clear()
            idRef.dispose()

            if (first) {
                return invoke(ctx, obj, method, payload, false)
            }
            return 0
        }

        return reqHold.id
    }

    private fun lookup(ctx: CValuesRef<ubus_context>, name: String, useCache: Boolean = true): UInt {
        if (useCache) {
            val cache = idCache.value[name]
            if (cache != null) {
                return cache
            }
        }

        return memScoped {
            val idRef = alloc<UIntVar>().ptr
            val id = if (ubus_lookup_id(ctx, name, idRef) != 0) {
                0u
            } else {
                idRef.pointed.value
            }
            val old = idCache.value
            val map = mutableMapOf<String, UInt>()
            old.forEach {
                map[it.key] = it.value
            }
            map[name] = id
            idCache.compareAndSet(old, map.freeze())

            id
        }

    }
}

data class InvokeReq(val id: Long, val scope: Arena = Arena()) {
    fun clear() {
        scope.clear()
    }
}