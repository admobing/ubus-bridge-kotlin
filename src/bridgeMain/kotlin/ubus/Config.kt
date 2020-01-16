package ubus

class Config(val server: String?) {

    val providers = mutableListOf<Provider>()

    val subscribers = mutableListOf<Subscriber>()
    
}