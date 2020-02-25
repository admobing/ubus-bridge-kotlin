package ubus

import Log.debug
import kotlinx.cinterop.*
import kotlinx.cinterop.internal.CCall
import platform.posix.memcpy
import kotlin.native.SharedImmutable
import kotlin.native.ThreadLocal
import kotlin.native.concurrent.*

internal class ProviderRequest(val req: CValuesRef<ubus_request_data>, val arena: Arena, val reqId: Long) {
    fun complete() {
        arena.clear()
    }
}

@SharedImmutable
internal val provideCounter = AtomicInt(0)

@SharedImmutable
internal val providerWorker = Worker.start()

@ThreadLocal
private val reqList = mutableListOf<ProviderRequest>()

internal class Provider(val pName: String, var methodList: List<Method>, private val registered: AtomicInt = AtomicInt(-1)) {
    companion object {
        fun rsp(ctx: CValuesRef<ubus_context>, reqId: Long, payload: String): Boolean {
            val future = providerWorker.execute(TransferMode.SAFE, {
                reqId
            }) { id ->
                reqList.find { it.reqId == id }?.apply {
                    reqList.remove(this)
                }
            }

            val req = future.result
            if (req == null) {
                debug("rsp ${reqId},find req null")
                return false
            }

            memScoped {
                val buf = alloc<blob_buf>().ptr
                blob_buf_init(buf, 0)
                if (!blobmsg_add_json_from_string(buf, payload)) {
                    error("invoke err. payload parse failed.")
                    return false
                }

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
    }

    private val ubus: CPointer<ubus_object> by lazy {
        val mList = nativeHeap.allocArray<ubus_method>(methodList.size) { mIndex ->
            val method = methodList[mIndex]
            val pList = nativeHeap.allocArray<blobmsg_policy>(method.params.size) { index ->
                val param = method.params[index]
                val namePtr = nativeHeap.allocArray<ByteVar>(param.name.length)
                param.name.cstr.place(namePtr)
                this.name = namePtr
                this.type = when (param.type) {
                    ParamType.INT -> 5u
                    ParamType.BOOL -> 7u
                    ParamType.STRING -> 3u
                    else -> 3u
                }
            }

            val namePtr = nativeHeap.allocArray<ByteVar>(method.name.length)
            method.name.cstr.place(namePtr)

            this.name = namePtr
            this.n_policy = method.params.size
            this.policy = pList
            this.handler = staticCFunction { ctx, obj, req, method, msg ->
                initRuntimeIfNeeded()

                val oid = obj!!.pointed.id
                val payload = blobmsg_format_json(msg, true)!!.toKString()
                val provider = Ubus.findProviderById(oid)
                if (provider == null) {
                    ubus_complete_deferred_request(ctx, req, 0)
                } else {
                    val proxy = Arena().run {
                        ProviderRequest(alloc<ubus_request_data>().ptr, this, provideCounter.addAndGet(1))
                    }
                    ubus_defer_request(ctx, req, proxy.req)
                    provider.onInvoke(method!!.toKString(), payload, proxy)
                }
                0
            }
        }

        val namePtr = nativeHeap.allocArray<ByteVar>(pName.length)
        pName.cstr.place(namePtr)

        val mType = nativeHeap.alloc<ubus_object_type> {
            this.name = namePtr
            this.n_methods = methodList.size
            this.methods = mList
        }.ptr

        nativeHeap.alloc<ubus_object> {
            this.type = mType
            this.name = namePtr
            this.methods = mList
            this.n_methods = methodList.size
        }.ptr
    }

    fun onInvoke(method: String, payload: String, req: ProviderRequest) {
        providerWorker.execute(TransferMode.SAFE, {
            req.freeze()
        }) {
            reqList.add(it)
        }

        getUbusListener().onRequestArrive(pName, method, payload, req.reqId)
    }

    fun toUbus(): CValuesRef<ubus_object> {
        return ubus
    }

    fun getUbusId(): UInt {
        return ubus.pointed.id
    }

    fun notify(ctx: CValuesRef<ubus_context>, type: String, payload: String) {
        memScoped {
            val buf = alloc<blob_buf>().ptr
            blob_buf_init(buf, 0)
            if (!blobmsg_add_json_from_string(buf, payload)) {
                debug("${pName} notify ${type} failed. failed to parse payload. ${payload}")
                return
            }

            val ret = ubus_notify(ctx, ubus, type, buf.pointed.head, -1)//timeout <0 no reply
            if (ret == 0) {
                debug("${pName} notify ${type} success. payload: ${payload}")
            } else {
                debug("${pName} notify ${type} failed. payload: ${payload}")
            }
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