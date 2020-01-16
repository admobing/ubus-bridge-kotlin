package ubus

import Log
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicLong
import kotlin.native.concurrent.DetachedObjectGraph
import kotlin.native.concurrent.attach

enum class ParamType {
    STRING, INT, BOOL;

    companion object {
        fun nameOf(type: String): ParamType {
            return when (type) {
                STRING.name -> STRING
                INT.name -> INT
                BOOL.name -> BOOL
                else -> STRING
            }
        }
    }
}

data class MethodParam(val name: String, val type: ParamType)

data class Method(val name: String, var params: List<MethodParam>)

class ProviderRequest(val req: CValuesRef<ubus_request_data>, val arena: Arena, val reqId: Long) {
    fun complete() {
        arena.clear()
    }
}

@SharedImmutable
internal val provideCounter = AtomicLong()

class Provider(val pName: String, var methodList: List<Method>, private val registered: AtomicInt = AtomicInt(-1)) {

    @kotlin.native.concurrent.SharedImmutable
    private var reqListRef = DetachedObjectGraph { mutableListOf<ProviderRequest>() }.asCPointer()

    private val ubus: CValuesRef<ubus_object> by lazy {
        val mList = nativeHeap.allocArray<ubus_method>(methodList.size) { mIndex ->
            val method = methodList[mIndex]
            val pList = nativeHeap.allocArray<blobmsg_policy>(method.params.size) { index ->
                val param = method.params[index]
                this.name = param.name.cstr.place(nativeHeap.allocArray(param.name.length))
                this.type = when (param.type) {
                    ParamType.INT -> 5u
                    ParamType.BOOL -> 7u
                    ParamType.STRING -> 3u
                    else -> 3u
                }
            }

            nativeHeap.alloc<ubus_method> {
                this.name = method.name.cstr.place(nativeHeap.allocArray(method.name.length))
                this.n_policy = method.params.size
                this.policy = pList
                this.handler = staticCFunction { ctx, obj, req, method, msg ->
                    initRuntimeIfNeeded()

                    val oid = obj!![0].id
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
            }
        }

        nativeHeap.alloc<ubus_object> {
            this.name = pName.cstr.place(nativeHeap.allocArray(pName.length))
            this.type = nativeHeap.alloc<ubus_object_type> {
                this.name = pName.cstr.place(nativeHeap.allocArray(pName.length))
                this.n_methods = methodList.size
                this.methods = mList
            }.ptr
            this.methods = mList
            this.n_methods = methodList.size
        }.ptr
    }

    fun onInvoke(method: String, payload: String, req: ProviderRequest) {
        DetachedObjectGraph<MutableList<ProviderRequest>>(reqListRef).attach().add(req)
        ubusListener?.onRequestArrive(pName, method, payload, req.reqId)
    }

    fun rsp(ctx: CValuesRef<ubus_context>, reqId: Long, payload: String): Boolean {
        val reqList = DetachedObjectGraph<MutableList<ProviderRequest>>(reqListRef).attach()
        val req = reqList.find {
            it.reqId == reqId
        }
        Log.debug("rsp ${reqId},find req:${req == null}")
        if (req == null) {
            return false
        }
        memScoped {
            val buf = cValue<blob_buf>().getPointer(this)
            blob_buf_init(buf, 0)
            buildUbusParam(payload, buf)
            val ret = ubus_send_reply(ctx, req.req, buf[0].head)
            if (ret != 0) {
                Log.debug("rsp err req:${reqId}, ret:${ret}")
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
            return ubus.getPointer(this)[0].id
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

        Log.debug("add provider ${pName} success, id: ${getUbusId()}")
    }
}