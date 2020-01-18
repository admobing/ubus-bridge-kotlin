//import kotlinx.cinterop.*
//import ubus.ubus_object
//import ubus.uloop_timeout
//import ubus.uloop_timeout_set
//
//fun main() {
//
//    val timer = nativeHeap.alloc<uloop_timeout> {
//        cb = staticCFunction { timer ->
//            initRuntimeIfNeeded()
//
//            uloop_timeout_set(timer, 2000)
//        }
//    }
//
//    println("${timer.time.tv_sec},${timer.time.tv_usec}")
//    val ret = memScoped {
//        uloop_timeout_set(timer.ptr, 1230)
//    }
//    println("${timer.time.tv_sec},${timer.time.tv_usec}")
//    println(ret)
//
//}