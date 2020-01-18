import platform.posix.sleep
import ubus.Method
import ubus.MethodParam
import ubus.ParamType
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

fun main() {
    Bridge.start("192.168.10.1:6791", { provider, method, payload, id ->
        Worker.start().execute(TransferMode.SAFE, {
            id
        }, {
            sleep(1u)
            val rsp = "[{\"name\":\"name\",\"type\":\"STRING\",\"value\":\"zhangsan\"},{\"name\":\"age\",\"type\":\"INT\",\"value\":18},{\"name\":\"man\",type:\"BOOL\",\"value\":\"false\"}]"
            Bridge.rsp(it, rsp)
        })
    }, { success, id, rsp, err ->
        println("invoke ${id} ${success},rsp: ${rsp}")
    }, { subscriber, method, payload ->

    })

    sleep(1u)

    Bridge.registerProvider("macos", listOf(
            Method("hello", listOf(
                    MethodParam("name", ParamType.STRING)
            ))
    ))
    Bridge.registerSubscriber("gpsd")

    Worker.start().execute(TransferMode.SAFE, {}, {
        while (true) {
            sleep(10u)
            Bridge.invoke("gpsd", "gpsinfo", "[]")
        }
    })

    sleep(1000u)
}
