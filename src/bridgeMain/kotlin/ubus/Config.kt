package ubus

internal class Config(val server: String?, val listener: Listener) {

    val providers = mutableListOf<Provider>()

    val subscribers = mutableListOf<Subscriber>()

    fun copy(): Config {
        val newCfg = Config(server, listener)
        newCfg.providers.addAll(this.providers)
        newCfg.subscribers.addAll(this.subscribers)

        return newCfg
    }
}