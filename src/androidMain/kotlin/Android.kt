import kotlinx.cinterop.*
import platform.android.JNIEnvVar
import platform.android.jobject
import platform.android.jstring
//import ubus.Method
//import ubus.MethodParam
//import ubus.ParamType

@CName("Java_com_wsg_hzgj_ubus_UbusDriver_test")
fun testKotlinJni(env: CPointer<JNIEnvVar>, thiz: jobject): jstring {
//    Bridge.registerProvider("macos", listOf(
//            Method("hello", listOf(
//                    MethodParam("name", ParamType.STRING)
//            ))
//    ))

    memScoped {
        return env.pointed.pointed!!.NewStringUTF!!.invoke(env, "This is from Kotlin Native!!".cstr.ptr)!!
    }
}

@CName("Java_com_wsg_hzgj_ubus_UbusDriver_load")
fun load(env: CPointer<JNIEnvVar>, thiz: jobject) {
//    Bridge.start("192.168.10.1:6791", { provider, method, payload, id ->
//        val rsp = "[{\"name\":\"name\",\"type\":\"STRING\",\"value\":\"zhangsan\"},{\"name\":\"age\",\"type\":\"INT\",\"value\":18},{\"name\":\"man\",type:\"BOOL\",\"value\":\"false\"}]"
//        Bridge.rsp(1, rsp)
//
//    }, { success, id, rsp, err ->
//        Log.debug("invoke ${id} ${success},rsp: ${rsp}")
//    }, { subscriber, method, payload ->
//
//    })

    Log.debug("load...")
}

