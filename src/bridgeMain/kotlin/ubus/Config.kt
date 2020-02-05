package ubus

internal class Config(val server: String?, val listener: Listener) {

    val providers = mutableListOf<Provider>()

    val subscribers = mutableListOf<Subscriber>()

    val eventListeners = mutableListOf<EventListener>()

    fun copy(): Config {
        val newCfg = Config(server, listener)
        newCfg.providers.addAll(this.providers)
        newCfg.subscribers.addAll(this.subscribers)
        newCfg.eventListeners.addAll(this.eventListeners)

        return newCfg
    }
}