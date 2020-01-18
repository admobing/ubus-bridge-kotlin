package ubus

import Log.debug
import kotlinx.cinterop.*
import kotlin.native.concurrent.*

internal class ProviderRequest(val req: CValuesRef<ubus_request_data>, val arena: Arena, val reqId: Long) {
    fun complete() {
        arena.clear()
    }
}

@SharedImmutable
internal val provideCounter = AtomicLong()

internal class Provider(val pName: String, var methodList: List<Method>, private val registered: AtomicInt = AtomicInt(-1)) {

    @kotlin.native.concurrent.SharedImmutable
    private var reqListRef = DetachedObjectGraph { mutableListOf<ProviderRequest>() }.asCPointer()


    private val ubus: CValuesRef<ubus_object> by lazy {
        //        val mList = nativeHeap.allocArray<ubus_method>(methodList.size) { mIndex ->
//            val method = methodList[mIndex]
//            val pList = nativeHeap.allocArray<blobmsg_policy>(method.params.size) { index ->
//                val param = method.params[index]
//                this.name = memScoped {
//                    param.name.cstr.ptr
//                }
//                this.type = when (param.type) {
//                    ParamType.INT -> 5u
//                    ParamType.BOOL -> 7u
//                    ParamType.STRING -> 3u
//                    else -> 3u
//                }
//            }
//
//            nativeHeap.alloc<ubus_method> {
//                this.name = memScoped {
//                    method.name.cstr.ptr
//                }
//                this.n_policy = method.params.size
//                this.policy = pList
//                this.handler = staticCFunction { ctx, obj, req, method, msg ->
//                    initRuntimeIfNeeded()
//
//                    val oid = obj!![0].id
//                    val payload = blobmsg_format_json(msg, true)!!.toKString()
//                    val provider = Ubus.findProviderById(oid)
//                    if (provider == null) {
//                        ubus_complete_deferred_request(ctx, req, 0)
//                    } else {
//                        val proxy = Arena().run {
//                            ProviderRequest(cValue<ubus_request_data>().getPointer(this), this, provideCounter.addAndGet(1))
//                        }
//                        ubus_defer_request(ctx, req, proxy.req)
//                        provider.onInvoke(method!!.toKString(), payload, proxy)
//                    }
//                    0
//                }
//            }
//        }
//
//        nativeHeap.alloc<ubus_object> {
//            val nativeType = nativeHeap.alloc<ubus_object_type> {
//                memScoped {
//                    name = pName.cstr.ptr
//                }
//                this.n_methods = methodList.size
//                this.methods = mList
//            }
//            memScoped {
//                type = nativeType.ptr
//                name = pName.cstr.ptr
//            }
//            this.methods = mList
//            this.n_methods = methodList.size
//        }

        val arena = Arena()
        val mList = methodList.map {
            val pList = it.params.map {
                cValue<blobmsg_policy> {
                    this.name = it.name.cstr.getPointer(arena)
                    this.type = when (it.type) {
                        ParamType.INT -> 5u
                        ParamType.BOOL -> 7u
                        ParamType.STRING -> 3u
                        else -> 3u
                    }
                }.getPointer(arena)
            }.toCValues().getPointer(arena)

            cValue<ubus_method> {
                this.name = it.name.cstr.getPointer(arena)
                this.n_policy = it.params.size
                this.policy = pList.pointed.value
                this.handler = staticCFunction { ctx, obj, req, method, msg ->
                    initRuntimeIfNeeded()

                    val oid = obj!!.pointed.id
                    val payload = blobmsg_format_json(msg, true)!!.toKString()
                    val provider = Ubus.findProviderById(oid)
                    if (provider == null) {
                        ubus_complete_deferred_request(ctx, req, 0)
                    } else {
                        val proxy = Arena().run {
                            ProviderRequest(cValue<ubus_request_data>().getPointer(this), this, provideCounter.addAndGet(1))
                        }
                        ubus_defer_request(ctx, req, proxy.req)
                        provider.onInvoke(method!!.toKString(), payload, proxy)
                    }
                    0
                }
            }.getPointer(arena)
        }.toCValues().getPointer(arena)

        cValue<ubus_object> {
            this.name = pName.cstr.getPointer(arena)
            this.type = cValue<ubus_object_type> {
                this.name = pName.cstr.getPointer(arena)
                this.n_methods = methodList.size
                this.methods = mList.pointed.value
            }.getPointer(arena)
            this.methods = mList.pointed.value
            this.n_methods = methodList.size
        }.getPointer(arena)
    }

    fun onInvoke(method: String, payload: String, req: ProviderRequest) {
        DetachedObjectGraph<MutableList<ProviderRequest>>(reqListRef).attach().add(req)
        getUbusListener().onRequestArrive(pName, method, payload, req.reqId)
    }

    fun rsp(ctx: CValuesRef<ubus_context>, reqId: Long, payload: String): Boolean {
        val reqList = DetachedObjectGraph<MutableList<ProviderRequest>>(reqListRef).attach()
        val req = reqList.find {
            it.reqId == reqId
        }
        if (req == null) {
            debug("rsp ${reqId},find req null")
            return false
        }

        memScoped {
            val buf = cValue<blob_buf>().getPointer(this)
            blob_buf_init(buf, 0)
            buildUbusParam(payload, buf)
            val ret = ubus_send_reply(ctx, req.req, buf.pointed.head)
            if (ret != 0) {
                debug("rsp err req:${reqId}, ret:${ret}")
            }
        }

        ubus_complete_deferred_request(ctx, req.req, 0)

        req.complete()
        reqList.remove(req)

        return true
    }

    fun toUbus(): CValuesRef<ubus_object> {
        return ubus
    }

    fun getUbusId(): UInt {
        memScoped {
            return ubus.getPointer(this).pointed.id
        }
    }

    fun onLeave() {
        registered.value = -1
    }

    fun isRegistered(): Boolean {
        return registered.value == 0
    }

    fun onRegiste() {
        registered.value = 0

        debug("add provider ${pName} success, id: ${getUbusId()}")
    }
}