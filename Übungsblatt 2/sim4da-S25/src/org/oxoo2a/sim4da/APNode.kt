package org.oxoo2a.sim4da

class APNode(name: String) : Node(name), DSMNode {

    private val localStore = mutableMapOf<String, Pair<String, Long>>()  // key → (value, timestamp)

    class WriteMessage : Message {
        val key: String
        val value: String
        val timestamp: Long

        constructor(key: String, value: String, timestamp: Long) : super() {
            this.key = key; this.value = value; this.timestamp = timestamp
        }
    }

    override fun engage() {
        Thread {
            val sim = Simulator.getInstance()
            while (sim.isSimulating) {
                val msg = receive()
                if (msg is WriteMessage) {
                    val ts = localStore[msg.key]?.second ?: Long.MIN_VALUE
                    if (msg.timestamp > ts) {
                        localStore[msg.key] = msg.value to msg.timestamp
                    }
                }
            }
        }.start()
    }

    override fun write(key: String, value: String) {
        val ts = System.currentTimeMillis()
        localStore[key] = value to ts // store immediately for max availability
        broadcast(WriteMessage(key, value, ts))
    }

    override fun read(key: String): String? =
        localStore[key]?.first

    override fun getLocalState(key: String): String? = read(key)

    override fun nodeName(): String = NodeName()
}