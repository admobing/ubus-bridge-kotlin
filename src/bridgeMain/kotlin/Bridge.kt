import ubus.*

object Bridge {
    fun start(server: String? = null, onRequestArrive: (provider: String, method: String, payload: String, id: Long) -> Unit, onInvokeCallback: (success: Boolean, id: Long, rsp: String?, err: String?) -> Unit, onNotifyArrive: (subscriber: String, method: String, payload: String) -> Unit, onEventArrive: (type: String, payload: String) -> Unit) {
        val listener = object : Listener {
            override fun onRequestArrive(provider: String, method: String, payload: String, id: Long) {
                onRequestArrive(provider, method, payload, id)
            }

            override fun onInvokeCallback(success: Boolean, id: Long, rsp: String?, err: String?) {
                onInvokeCallback(success, id, rsp, err)
            }

            override fun onEventArrive(type: String, payload: String) {
                onEventArrive(type, payload)
            }

            override fun onNotifyArrive(subscriber: String, method: String, payload: String) {
                onNotifyArrive(subscriber, method, payload)
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

    fun registerEventListener(name: String, path: String) {
        Ubus.registerEventListener(EventListener(name, path))
    }

    fun rsp(reqId: Long, payload: String) {
        Ubus.rsp(reqId, payload)
    }

    fun notify(provider: String, type: String, payload: String) {
        Ubus.notify(provider, type, payload)
    }

    fun sendEvent(event: String, payload: String) {
        Ubus.sendEvent(event, payload)
    }

}