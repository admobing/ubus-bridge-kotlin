package ubus

import Log.debug
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicReference

internal enum class SubState {
    REGISTERED, SUBSCRIBED, LEAVED
}

internal class Subscriber internal constructor(val sName: String, private val subState: AtomicReference<SubState> = AtomicReference(SubState.LEAVED)) {

    private val ubus by lazy {
        //        nativeHeap.alloc<ubus_subscriber> {
//            remove_cb = staticCFunction { ctx, sub, id ->
//                initRuntimeIfNeeded()
//                val oid = memScoped {
//                    sub!!.getPointer(this)[0].obj.id
//                }
//
//                Ubus.findSubscriberById(oid)?.onLeave()
//            }
//            cb = staticCFunction { ctx, obj, req, method, msg ->
//                initRuntimeIfNeeded()
//
//                val oid = obj!![0].id
//                val payload = blobmsg_format_json(msg, true)!!.toKString()
//                Ubus.findSubscriberById(oid)?.onNotify(method!!.toKString(), payload)
//                0
//            }
//        }.ptr

        val arena = Arena()
        cValue<ubus_subscriber> {
            remove_cb = staticCFunction { ctx, sub, id ->
                initRuntimeIfNeeded()
                val oid = memScoped {
                    sub!!.getPointer(this).pointed.obj.id
                }

                Ubus.findSubscriberById(oid)?.onLeave()
            }
            cb = staticCFunction { ctx, obj, req, method, msg ->
                initRuntimeIfNeeded()

                val oid = obj!!.pointed.id
                val payload = blobmsg_format_json(msg, true)!!.toKString()
                Ubus.findSubscriberById(oid)?.onNotify(method!!.toKString(), payload)
                0
            }
        }.getPointer(arena)
    }

    fun onNotify(method: String, payload: String) {
        getUbusListener().onEventArrive(sName, method, payload)
    }

    fun toUbus(): CValuesRef<ubus_subscriber> {
        return ubus
    }

    fun getUbusId(): UInt {
        return ubus.pointed.obj.id
    }

    fun onLeave() {
        subState.value = SubState.LEAVED
        debug("subscriber ${sName} leave ")
    }

    fun onSubscribe() {
        subState.value = SubState.SUBSCRIBED
        debug("subscriber ${sName} subscribe success")
    }

    fun onRegiste() {
        subState.value = SubState.REGISTERED
        debug("add subscriber ${sName} success, id: ${getUbusId()}")
    }

    fun isRegistered(): Boolean {
        return subState.value == SubState.REGISTERED
    }

    fun isSubscribed(): Boolean {
        return subState.value == SubState.SUBSCRIBED
    }

}