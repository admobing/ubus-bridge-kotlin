import ubus.*

object Bridge {
    fun start(server: String? = null, onRequestArrive: (provider: String, method: String, payload: String, id: Long) -> Unit, onInvokeCallback: (success: Boolean, id: Long, rsp: String?, err: String?) -> Unit, onEventArrive: (subscriber: String, method: String, payload: String) -> Unit) {
        val listener = object : Listener {
            override fun onRequestArrive(provider: String, method: String, payload: String, id: Long) {
                onRequestArrive(provider, method, payload, id)
            }

            override fun onInvokeCallback(success: Boolean, id: Long, rsp: String?, err: String?) {
                onInvokeCallback(success, id, rsp, err)
            }

            override fun onEventArrive(subscriber: String, method: String, payload: String) {
                onEventArrive(subscriber, method, payload)
            }

        }
        Ubus.run(server, listener)
    }

    fun invoke(obj: String, method: String, payload: String): Long {
        return Ubus.invoke(obj, method, payload)
    }

    fun registerProvider(name: String, methodList: List<Method>) {
        Ubus.registerProvider(Provider(name, methodList))
    }

    fun registerSubscriber(name: String) {
        Ubus.registerSubscriber(Subscriber(name))
    }

    fun rsp(reqId: Long, payload: String) {
        Ubus.rsp(reqId, payload)
    }

}