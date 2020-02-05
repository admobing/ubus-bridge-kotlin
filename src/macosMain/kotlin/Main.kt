import kotlinx.cinterop.toKString
import platform.posix.sleep
import ubus.Method
import ubus.MethodParam
import ubus.ParamType
import ubus.uloop_run
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

fun main() {
    Bridge.start(null, { provider, method, payload, id ->
        Worker.start().execute(TransferMode.SAFE, {
            id
        }, {
            println("in work: ${Worker.current.name}")
            sleep(1u)
//            val rsp = "[{\"name\":\"name\",\"type\":\"STRING\",\"value\":\"zhangsan\"},{\"name\":\"age\",\"type\":\"INT\",\"value\":18},{\"name\":\"man\",type:\"BOOL\",\"value\":\"false\"}]"
            val rsp = "{\"name\":\"zhangsan\",\"age\":123,\"man\":true}"
            Bridge.rsp(it, rsp)
        })
    }, { success, id, rsp, err ->
        println("invoke ${id} ${success},rsp: ${rsp}")
    }, { subscriber, method, payload ->
        println("sub from ${subscriber}, ${method}, ${payload}")
    }, { type, payload ->
        println("rec event ${type}, payload: ${payload}")
    })

    sleep(1u)

    Bridge.registerProvider("macos", listOf(
            Method("hello", listOf(
                    MethodParam("name", ParamType.STRING)
            ))
    ))
//
//    Worker.start().execute(TransferMode.SAFE, {}, {
//        var count = 0
//        while (true) {
//            sleep(1u)
//            Bridge.notify("macos", "testnotify", "{\"name\":\"zhangsan\",\"count\":${count++}}")
//        }
//    })

//    Bridge.registerSubscriber("macos")

    Bridge.registerEventListener("testev", "test")
    sleep(2u)
    uloop_run()

    Worker.start().execute(TransferMode.SAFE, {}, {
        while (true) {
            sleep(1u)
//            val reqId = Bridge.invoke("test", "count", "[{\"name\":\"to\",\"type\":\"INT\",\"value\":123},{\"name\":\"string\",\"type\":\"STRING\",\"value\":\"abc\"}]")
//            val reqId = Bridge.invoke("test", "count", "{\"to\":123,\"string\":\"abcd\"}")
//            println("req : ${reqId}")

            val reqId = Bridge.invoke("test", "count", "{\"to\":123,\"string\":\"abcd\"}")
            println("req : ${reqId}")

        }
    })

//    Worker.start().execute(TransferMode.SAFE, {}, {
//        var count = 0
//        while (true) {
//            sleep(1u)
//            Bridge.sendEvent("testev", "{\"name\":\"zhangsan\",\"count\":${count++}}")
//        }
//    })

    sleep(1000u)
}
