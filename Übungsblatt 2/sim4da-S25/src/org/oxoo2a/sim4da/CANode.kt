package org.oxoo2a.sim4da

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class CANode(name: String) : Node(name), DSMNode {
    private val quorumSize = Network.getInstance().numberOfNodes() / 2 + 1
    private val localStore = mutableMapOf<String, Pair<String, Long>>()

    private val pendingRequests = mutableMapOf<String, CountDownLatch>()

    class WriteRequest : Message {
        val key: String
        val value: String
        val ts: Long
        val reqId: String
        val origin: String
        constructor(key: String, value: String, ts: Long, reqId: String, origin: String) : super() {
            this.key = key; this.value = value; this.ts = ts; this.reqId = reqId; this.origin = origin
        }
    }

    class WriteAck : Message {
        var reqId: String
        constructor(reqId: String) : super() { this.reqId = reqId }
        constructor(other: WriteAck) : super(other) { reqId = other.reqId } // sim4da requires a copy constructor for sendBlindly
    }

    override fun engage() {
        Thread {
            val sim = Simulator.getInstance()
            while (sim.isSimulating) {
                val msg = receive()
                when (msg) {
                    is WriteRequest -> {
                        synchronized(localStore) {
                            val ts = localStore[msg.key]?.second ?: Long.MIN_VALUE
                            if (msg.ts > ts) localStore[msg.key] = msg.value to msg.ts
                        }
                        sendBlindly(WriteAck(msg.reqId), msg.origin)
                    }
                    is WriteAck -> {
                        synchronized(pendingRequests) {
                            pendingRequests[msg.reqId]?.countDown()
                        }
                    }
                    else -> { }
                }
            }
        }.start()
    }

    override fun write(key: String, value: String) {
        val ts = System.currentTimeMillis()
        val reqId = "${nodeName()}:W:$ts"

        synchronized(localStore) {
            localStore[key] = value to ts
        }

        val latch = CountDownLatch(quorumSize - 1)
        synchronized(pendingRequests) {
            pendingRequests[reqId] = latch
        }
        broadcast(WriteRequest(key, value, ts, reqId, nodeName()))

        val success = latch.await(2, TimeUnit.SECONDS)
        if (!success) {
            println("Node ${nodeName()} - WRITE TIMEOUT on key $key")
        }

        synchronized(pendingRequests) {
            pendingRequests.remove(reqId)
        }
    }

    override fun read(key: String): String? =
        synchronized(localStore) {
            localStore[key]?.first
        }

    override fun getLocalState(key: String): String? = read(key)

    override fun nodeName(): String = NodeName()
}