import kotlinx.cinterop.*
import platform.posix.sleep
import ubus.*
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

@ThreadLocal
object Ubus {
    private var ubusServer: String? = null // null eq /var/run/ubus.sock
    private var ctx: CPointer<ubus_context>? = null

    private lateinit var client: CValue<ubus_object>
    private lateinit var subscriber: CValue<ubus_subscriber>

    private fun config(): UbusConfig {
        val client = cValue<ubus_object> {

        }
        val subscriber = cValue<ubus_subscriber> {
            cb = staticCFunction { ctx, obj, req, method, msg ->
                initRuntimeIfNeeded()
                val notification = blobmsg_format_json(msg, true)
                Log.debug("receive notification ${method}, ${notification}")
                0
            }
        }

        return UbusConfig(null, client, subscriber)
    }

    fun run(server: String? = null) {
        Worker.start().execute(TransferMode.SAFE, {
            config()
        }) {
            ubusServer = it.server
            client = it.client
            subscriber = it.subscriber

            uloop_init()
            start()
            Log.debug("uloop start")
            uloop_run()
        }
    }

    fun count(): String {
        if (ctx == null) {
            return "not init"
        }
        val buf = cValue<blob_buf>()
        blob_buf_init(buf, 0)
        blobmsg_add_u32(buf, "to", 9527u)
        blobmsg_add_string(buf, "string", "hello")
        Log.debug("build")

//        val id: CValue<UIntVar> = cValue()
//
//        Log.debug("begin lookup")
//        if (ubus_lookup_id(ctx, "test", id) == 0) {
//            return "not found"
//        }
//        Log.debug("lookup")

//        val msg = buf.useContents {
//            this.head
//        }
//
//        val ret = ubus_invoke(ctx, id.placeTo(Arena())[0], "count", msg, staticCFunction { req, type, rsp ->
//            initRuntimeIfNeeded()
//
//            val returnPolicy = createValues<blobmsg_policy>(1) { index ->
//                this.type = 0u
//                this.name = "rc".cstr.placeTo(Arena())
//            }
//            val data = createValues<CPointerVar<blob_attr>>(1) {}
//            val ret = blobmsg_parse(returnPolicy, 1, data, blob_data(rsp), blob_len(rsp))
//            if (ret != 0) {
//                Log.debug("invoke parse ret: ${ret}")
//                return@staticCFunction
//            }
//
//            Log.debug("invoke ret rc: ${blobmsg_get_u32(data.placeTo(Arena())[0])}")
//        }, null, 1000)

        val ret = 0
        return "invoke count : ${ret}"
    }

    private fun start() {
        Log.debug("ubus connect ${ubusServer} ...")
        val ret = ubus_connect(ubusServer)
        if (ret == null) {
            restart()
            return
        }

        Log.debug("ubus connect success")
        ret.pointed.connection_lost = staticCFunction { _ ->
            initRuntimeIfNeeded()
            Log.debug("ubus disconnect")

            restart()
        }

        ubus_add_uloop(ret)
        if (ubus_add_object(ret, client) != 0) {
            error("add object failed")
            restart()
            return
        }

        ctx = ret

        count()
    }

    private fun restart() {
        stop()
        sleep(1u) // delay 1s
        start()
    }

    fun stop() {
        ctx?.let {
            ubus_free(ctx)
            ctx = null
        }
    }

}

data class UbusConfig(val server: String?, val client: CValue<ubus_object>, val subscriber: CValue<ubus_subscriber>)