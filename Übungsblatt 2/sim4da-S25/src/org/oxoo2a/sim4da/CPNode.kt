package org.oxoo2a.sim4da

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CPNode(name: String) : Node(name), DSMNode {
    private val quorumSize = Network.getInstance().numberOfNodes() / 2 + 1
    private val localStore = mutableMapOf<String, Pair<String, Long>>()  // key → (value, timestamp)

    private data class RequestState(
        val latch: CountDownLatch,
        val responses: MutableList<Pair<String, Long>>
    )
    private val pendingRequests = mutableMapOf<String, RequestState>()

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

    class ReadRequest : Message {
        var key: String
        var reqId: String
        var origin: String
        constructor(key: String, reqId: String, origin: String) : super() {
            this.key = key; this.reqId = reqId; this.origin = origin
        }
    }

    class ReadResponse : Message {
        var key: String
        var value: String
        var ts: Long
        var reqId: String
        var origin: String
        constructor(key: String, value: String, ts: Long, reqId: String, origin: String) : super() {
            this.key = key; this.value = value; this.ts = ts; this.reqId = reqId; this.origin = origin
        }
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
                    is ReadRequest -> {
                        val (value, ts) = synchronized(localStore) {
                            localStore[msg.key] ?: ("" to 0L)
                        }
                        sendBlindly(
                            ReadResponse(msg.key, value, ts, msg.reqId, nodeName()),
                            msg.origin
                        )
                    }
                    is WriteAck -> {
                        synchronized(pendingRequests) {
                            pendingRequests[msg.reqId]?.latch?.countDown()
                        }
                    }
                    is ReadResponse -> {
                        synchronized(pendingRequests) {
                            pendingRequests[msg.reqId]?.let { state ->
                                state.responses += msg.value to msg.ts
                                state.latch.countDown()
                            }
                        }
                    }
                    else -> {}
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
            pendingRequests[reqId] = RequestState(latch, mutableListOf())
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

    override fun read(key: String): String? {
        val ts = System.currentTimeMillis()
        val reqId = "${nodeName()}:R:$ts"

        val responses = mutableListOf<Pair<String, Long>>()
        val latch = CountDownLatch(quorumSize - 1)
        synchronized(pendingRequests) {
            pendingRequests[reqId] = RequestState(latch, responses)
        }

        broadcast(ReadRequest(key, reqId, nodeName()))

        val success = latch.await(2, TimeUnit.SECONDS)
        if (!success) {
            return getLocalState(key)
        }

        synchronized(localStore) {
            localStore[key]?.let { responses += it }
        }
        val (bestValue, bestTs) = responses.maxByOrNull { it.second }!!
        synchronized(localStore) {
            localStore[key] = bestValue to bestTs
        }

        synchronized(pendingRequests) {
            pendingRequests.remove(reqId)
        }
        return bestValue
    }

    override fun getLocalState(key: String): String? =
        synchronized(localStore) { localStore[key]?.first }

    override fun nodeName(): String = NodeName()
}