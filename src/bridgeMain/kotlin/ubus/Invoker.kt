package ubus

import Log.debug
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicLong

//@SharedImmutable
//internal val idCache = AtomicReference<MutableMap<String, Int>>(HashMap())

@SharedImmutable
internal val reqCounter = AtomicLong()

@ThreadLocal
internal object Invoker {
    fun invoke(ctx: CValuesRef<ubus_context>, obj: String, method: String, payload: String, first: Boolean = true): Long {
        val id = lookup(ctx, obj, first)
        if (id == 0) {
            debug("invoke err, lookup ${obj} failed")
            return 0
        }

        val reqId = reqCounter.addAndGet(1)

        val req = cValue<ubus_request> {
            priv = StableRef.create(reqId).asCPointer()

            data_cb = staticCFunction { req, type, msg ->
                initRuntimeIfNeeded()

                memScoped {
                    val idRef = req!!.pointed.priv!!.asStableRef<Long>()
                    getUbusListener().onInvokeCallback(true, idRef.get(), blobmsg_format_json(msg, true)!!.toKString())
                    idRef.dispose()
                }
            }

            complete_cb = staticCFunction { req, ret ->
                initRuntimeIfNeeded()
                if (ret != 0) {
                    memScoped {
                        val idRef = req!!.pointed.priv!!.asStableRef<Long>()
                        getUbusListener().onInvokeCallback(false, idRef.get())
                        idRef.dispose()
                    }
                }
            }
        }

        var invoke = 0
        memScoped {
            val buf = cValue<blob_buf>().getPointer(this)
            blob_buf_init(buf, 0)
            buildUbusParam(payload, buf)

            invoke = ubus_invoke_async(ctx, id.toUInt(), obj, buf.pointed.head, req)
            if (invoke == 0) {
                ubus_complete_request_async(ctx, req)
            }
        }

        if (invoke != 0) {
            debug("invoke err. ${ubus_strerror(invoke)?.toKString()}")
            memScoped {
                req.useContents { priv }!!.asStableRef<Long>().dispose()
            }

            if (first) {
                return invoke(ctx, obj, method, payload, false)
            }
            return 0
        }

        return reqId
    }

    private fun lookup(ctx: CValuesRef<ubus_context>, name: String, useCache: Boolean = true): Int {
//        if (useCache) {
//            val map = idCache.value
//            if (map.containsKey(name)) {
//                return map[name]!!
//            }
//        }

        return memScoped {
            val idRef = cValue<UIntVar>().getPointer(this)
            val id = if (ubus_lookup_id(ctx, name, idRef) != 0) {
                0
            } else {
                idRef.pointed.value.toInt()
            }
//            val old = idCache.value
//            val map = mutableMapOf<String, Int>()
//            old.forEach {
//                map[it.key] = it.value
//            }
//            map[name] = id
//            idCache.value = map

            id
        }

    }
}