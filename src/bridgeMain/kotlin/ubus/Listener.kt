package ubus

interface Listener {

    fun onRequestArrive(provider: String, method: String, payload: String, id: Long)

    fun onInvokeCallback(success: Boolean, id: Long, rsp: String? = null, err: String? = null)

    fun onEventArrive(subscriber: String, method: String, payload: String)

}