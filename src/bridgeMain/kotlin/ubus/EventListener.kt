package ubus

import kotlinx.cinterop.*
import kotlin.native.concurrent.AtomicInt

internal class EventListener(val name: String, val path: String, private val registered: AtomicInt = AtomicInt(-1)) {

    private val ubus: CPointer<ubus_event_handler> by lazy {
        nativeHeap.alloc<ubus_event_handler> {
            this.cb = staticCFunction { ctx, ev, type, msg ->
                initRuntimeIfNeeded()

                val payload = blobmsg_format_json(msg, true)!!.toKString()
                type!!.toKString()

                getUbusListener().onEventArrive(type!!.toKString(), payload)
            }
        }.ptr
    }

    fun toBus(): CPointer<ubus_event_handler> {
        return ubus
    }

    fun getUbusId(): UInt {
        return ubus.pointed.obj.id
    }

    fun onLeave() {
        registered.value = -1
    }

    fun isRegistered(): Boolean {
        return registered.value == 0
    }

    fun onRegiste() {
        registered.value = 0

        Log.debug("add event listener ${name} success, id: ${getUbusId()}")
    }

}