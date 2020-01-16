import platform.posix.sleep
import ubus.Ubus

fun main() {
    val job = Ubus.run("192.168.10.1:6791")

//    val ret = Ubus.registerProvider(Provider("screend", listOf(
//            Method("hello", listOf(
//                    MethodParam("name", ParamType.STRING)
//            ))
//    )))
//
//    Log.debug("ret: ${ret}")

//    while (true) {
//        val rsp = "[{\"name\":\"name\",\"type\":\"STRING\",\"value\":\"zhangsan\"},{\"name\":\"age\",\"type\":\"INT\",\"value\":18},{\"name\":\"man\",type:\"BOOL\",\"value\":\"false\"}]"
//        Ubus.rsp(1, rsp)
//        sleep(1u)
//    }

    job.consume { }
}