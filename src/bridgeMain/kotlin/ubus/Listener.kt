package ubus

interface Listener {

    fun onRequestArrive(provider: String, method: String, payload: String, id: Long)

    fun onInvokeCallback(success: Boolean, id: Long, rsp: String? = null, err: String? = null)

    fun onEventArrive(type: String, payload: String)

    fun onNotifyArrive(subscriber: String, method: String, payload: String)

}