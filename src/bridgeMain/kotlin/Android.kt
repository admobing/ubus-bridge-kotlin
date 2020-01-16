//import kotlinx.cinterop.*
//import platform.android.JNIEnvVar
//import platform.android.jobject
//import platform.android.jstring
//import kotlinx.serialization.*
//
//import ubus.Ubus
//
//@CName("Java_com_wsg_hzgj_ubus_UbusDriver_test")
//fun testKotlinJni(env: CPointer<JNIEnvVar>, thiz: jobject): jstring {
//    memScoped {
//        return env.pointed.pointed!!.NewStringUTF!!.invoke(env, "This is from Kotlin Native!!".cstr.ptr)!!
//    }
//}
//
//@CName("Java_com_wsg_hzgj_ubus_UbusDriver_load")
//fun load(env: CPointer<JNIEnvVar>, thiz: jobject) {
//    Ubus.run("192.168.10.1:6791")
//}
//
