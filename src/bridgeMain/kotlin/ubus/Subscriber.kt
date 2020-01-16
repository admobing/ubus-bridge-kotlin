package ubus

import Log
import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicReference

enum class SubState {
    REGISTERED, SUBSCRIBED, LEAVED
}

class Subscriber internal constructor(val sName: String, private val subState: AtomicReference<SubState> = AtomicReference(SubState.LEAVED)) {
    private val ubus by lazy {
        nativeHeap.alloc<ubus_subscriber> {
            remove_cb = staticCFunction { ctx, sub, id ->
                initRuntimeIfNeeded()
                val oid = memScoped {
                    sub!!.getPointer(this)[0].obj.id
                }

                Ubus.findSubscriberById(oid)?.onLeave()
            }
            cb = staticCFunction { ctx, obj, req, method, msg ->
                initRuntimeIfNeeded()

                val oid = obj!![0].id ?: 0u
                val payload = blobmsg_format_json(msg, true)!!.toKString()
                Ubus.findSubscriberById(oid)?.onNotify(method!!.toKString(), payload)
                0
            }
        }.ptr
    }

    fun onNotify(method: String, payload: String) {
        ubusListener?.onEventArrive(sName, method, payload)
    }

    fun toUbus(): CValuesRef<ubus_subscriber> {
        return ubus
    }

    fun getUbusId(): UInt {
        return ubus[0].obj.id
    }

    fun onLeave() {
        subState.value = SubState.LEAVED
        Log.debug("subscriber ${sName} leave ")
    }

    fun onSubscribe() {
        subState.value = SubState.SUBSCRIBED
        Log.debug("subscriber ${sName} subscribe success")
    }

    fun onRegiste() {
        subState.value = SubState.REGISTERED
        Log.debug("add object ${sName} success, id: ${getUbusId()}")
    }

    fun isRegistered(): Boolean {
        return subState.value == SubState.REGISTERED
    }

    fun isSubscribed(): Boolean {
        return subState.value == SubState.SUBSCRIBED
    }

}