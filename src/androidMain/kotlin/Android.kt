import platform.android.*
import kotlinx.cinterop.CPointer
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlin.native.concurrent.freeze

@SharedImmutable
lateinit var onRequestArrive: JniMethod
@SharedImmutable
lateinit var onInvokeCallback: JniMethod
@SharedImmutable
lateinit var onNotifyArrive: JniMethod
@SharedImmutable
lateinit var onEventArrive: JniMethod

@CName("Java_com_wthink_ubus_UbusBridge_load")
fun load(env: CPointer<JNIEnvVar>, cls: jclass, server: jstring?) = jniWith(env) {
    onRequestArrive = getStaticMethodID(cls, "onRequestArrive", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;J)V")!!.freeze()
    onInvokeCallback = getStaticMethodID(cls, "onInvokeCallback", "(ZJLjava/lang/String;Ljava/lang/String;)V")!!.freeze()
    onNotifyArrive = getStaticMethodID(cls, "onNotifyArrive", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V")!!.freeze()
    onEventArrive = getStaticMethodID(cls, "onEventArrive", "(Ljava/lang/String;Ljava/lang/String;)V")!!.freeze()

    Bridge.start(server?.asKString(), { provider, method, payload, id ->
        jniWith(env) {
            callStaticVoidMethod(cls, onRequestArrive, provider, method, payload, id)
        }
    }, { success, id, rsp, err ->
        jniWith(env) {
            callStaticVoidMethod(cls, onInvokeCallback, success, id, rsp, err)
        }
    }, { subscriber, method, payload ->
        jniWith(env) {
            callStaticVoidMethod(cls, onNotifyArrive, subscriber, method, payload)
        }
    }, { type, payload ->
        jniWith(env) {
            callStaticVoidMethod(cls, onEventArrive, type, payload)
        }
    })
}

@ImplicitReflectionSerializer
@CName("Java_com_wthink_ubus_UbusBridge_registerProvider")
fun registerProvider(env: CPointer<JNIEnvVar>, cls: jclass, name: jstring, methodSig: jstring) = jniWith(env) {
    Bridge.registerProvider(name.asKString()!!, parseMethodSig(methodSig.asKString()!!))
}

@CName("Java_com_wthink_ubus_UbusBridge_invoke")
fun invoke(env: CPointer<JNIEnvVar>, cls: jclass, obj: jstring, method: jstring, payload: jstring): jlong = jniWith(env) {
    return@jniWith Bridge.invoke(obj.asKString()!!, method.asKString()!!, payload.asKString()!!)
}

@CName("Java_com_wthink_ubus_UbusBridge_registerSubscriber")
fun registerSubscriber(env: CPointer<JNIEnvVar>, cls: jclass, name: jstring) = jniWith(env) {
    Bridge.registerSubscriber(name.asKString()!!)
}

@CName("Java_com_wthink_ubus_UbusBridge_registerEventListener")
fun registerEventListener(env: CPointer<JNIEnvVar>, cls: jclass, name: jstring, path: jstring) = jniWith(env) {
    Bridge.registerEventListener(name.asKString()!!, path.asKString()!!)
}

@CName("Java_com_wthink_ubus_UbusBridge_rsp")
fun rsp(env: CPointer<JNIEnvVar>, cls: jclass, reqId: jlong, payload: jstring) = jniWith(env) {
    Bridge.rsp(reqId, payload.asKString()!!)
}

@CName("Java_com_wthink_ubus_UbusBridge_notify")
fun notify(env: CPointer<JNIEnvVar>, cls: jclass, provider: jstring, type: jstring, payload: jstring) = jniWith(env) {
    Bridge.notify(provider.asKString()!!, type.asKString()!!, payload.asKString()!!)
}

@CName("Java_com_wthink_ubus_UbusBridge_sendEvent")
fun sendEvent(env: CPointer<JNIEnvVar>, cls: jclass, event: jstring, payload: jstring) = jniWith(env) {
    Bridge.sendEvent(event.asKString()!!, payload.asKString()!!)
}