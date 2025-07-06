package org.oxoo2a.sim4da

import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.lang.reflect.Field
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import java.util.Collections

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation::class)
class ExperimentTest {

    private companion object {
        const val ANSI_RESET = "\u001B[0m"
        const val ANSI_RED   = "\u001B[31m"
        const val ANSI_GREEN = "\u001B[32m"
        const val ANSI_YELLOW = "\u001B[33m"
    }

    private val partitionedQueues = mutableMapOf<String, PartitionedList>()

    class PartitionedList(
        private val original: MutableList<Any>,
        private val selfName: String,
        private val groupA: Set<String>,
        private val groupB: Set<String>
    ) : MutableList<Any> by original {
        var isPartitioned = true

        override fun add(element: Any): Boolean {
            if (isPartitioned) {
                val senderField = element.javaClass.getDeclaredField("sender")
                senderField.isAccessible = true
                val senderName = (senderField.get(element) as NetworkConnection).NodeName()

                if ((senderName in groupA && selfName in groupB) ||
                    (senderName in groupB && selfName in groupA)) {
                    return false
                }
            }
            return original.add(element)
        }
    }

    @BeforeEach
    fun resetSim() {
        val simCls = Simulator::class.java
        val instField: Field = simCls.getDeclaredField("instance")
        instField.isAccessible = true
        instField.set(null, null)
        val behCls = SimulationBehavior::class.java
        val distField: Field = behCls.getDeclaredField("r_message_queue_selection")
        distField.isAccessible = true
        distField.set(null, null)
        partitionedQueues.clear()
    }

    enum class Variant {
        AP { override fun create(id: String): DSMNode = APNode(id) },
        CP { override fun create(id: String): DSMNode = CPNode(id) },
        CA { override fun create(id: String): DSMNode = CANode(id) };
        abstract fun create(id: String): DSMNode
    }

    data class ExperimentStats(
        val totalSnapshots: Int,
        val inconsistentSnapshots: Int
    )

    @Order(1)
    @ParameterizedTest(name = "Low Latency - No Partition - {0}")
    @EnumSource(Variant::class)
    fun low_Latency_no_Partition(variant: Variant) {
        println("\nTest: Low Latency, No Partition | ${variant.name}")

        val stats = runExperiment(variant)

        println("Result: ${stats.inconsistentSnapshots} / ${stats.totalSnapshots} Snapshots inconsistent")
        println("Rate: %.2f%%".format(stats.inconsistentSnapshots * 100.0 / stats.totalSnapshots))

        Assertions.assertTrue(true)
    }

    @Order(3)
    @ParameterizedTest(name = "High Latency - No Partition - {0}")
    @EnumSource(Variant::class)
    fun high_Latency_no_Partition(variant: Variant) {
        println("\nTest: High Latency, No Partition | ${variant.name}")

        SimulationBehavior.setMessageQueueSelectionDistributionFunction(
            RandomValues.getUniformDistribution()
        )

        val stats = runExperiment(variant)

        println("Result: ${stats.inconsistentSnapshots} / ${stats.totalSnapshots} Snapshots inconsistent")
        println("Rate: %.2f%%".format(stats.inconsistentSnapshots * 100.0 / stats.totalSnapshots))

        Assertions.assertTrue(true)
    }

    @Order(2) //2
    @ParameterizedTest(name = "Low Latency - Temp Partition - {0}")
    @EnumSource(Variant::class)
    fun low_Latency_temporary_Partition(variant: Variant) {
        println("\nTest: Low Latency + Temp. Partition | ${variant.name}")

        val stats = runExperiment(
            variant       = variant,
            numNodes      = 10,
            partitionGroups = setOf("0","1","2","3","4") to setOf("5","6","7","8","9"),
            healAfterMs     = 15000L
        )

        println("Result: ${stats.inconsistentSnapshots} / ${stats.totalSnapshots} Snapshots inconsistent")
        println("Rate: %.2f%%".format(stats.inconsistentSnapshots * 100.0 / stats.totalSnapshots))

        Assertions.assertTrue(true)
    }

    @Order(4)
    @ParameterizedTest(name = "High Latency - TempPartition - {0}")
    @EnumSource(Variant::class)
    fun high_Latency_temporary_Partition(variant: Variant) {
        println("\nest: High Latency + Temp. Partition | ${variant.name}")

        SimulationBehavior.setMessageQueueSelectionDistributionFunction(
            RandomValues.getUniformDistribution()
        )

        val stats = runExperiment(
            variant       = variant,
            numNodes      = 10,
            partitionGroups = setOf("0","1","3","5","7","9") to setOf("2","4","6","8"),
            healAfterMs     = 5000L
        )

        println("Result: ${stats.inconsistentSnapshots} / ${stats.totalSnapshots} Snapshots inconsistent")
        println("Rate: %.2f%%".format(stats.inconsistentSnapshots * 100.0 / stats.totalSnapshots))

        Assertions.assertTrue(true)
    }

    private fun installPartition(groupA: Set<String>, groupB: Set<String>) {
        val network = Network.getInstance()
        val nodesField = network.javaClass.getDeclaredField("nodes")
        nodesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val nodesMap = nodesField.get(network) as Map<String, Any>

        for ((nodeName, nodeRec) in nodesMap) {
            val np = nodeRec.javaClass.getMethod("np").invoke(nodeRec) as NodeProxy
            val msgField = NodeProxy::class.java.getDeclaredField("messages")
            msgField.isAccessible = true
            val original = msgField.get(np) as MutableList<Any>

            val partitionedList = PartitionedList(original, nodeName, groupA, groupB)
            partitionedQueues[nodeName] = partitionedList
            msgField.set(np, partitionedList)
        }
    }

    private fun healPartition() {
        partitionedQueues.values.forEach { it.isPartitioned = false }
    }

    private fun runExperiment(
        variant: Variant,
        numNodes: Int = 10,
        durationSec: Long = 30L,
        writerSleepMs: Long = 100L,
        partitionGroups: Pair<Set<String>,Set<String>>? = null,
        healAfterMs: Long? = null
    ): ExperimentStats {
        val sim = Simulator.getInstance()
        val ids = (0 until numNodes).map(Int::toString)
        val nodes = ids.map { variant.create(it) }

        partitionGroups?.let { (groupA, groupB) ->
            installPartition(groupA, groupB)
            healAfterMs?.let { ms ->
                Thread {
                    Thread.sleep(ms)
                    healPartition()
                    println("$ANSI_YELLOW>>> Partition healed <<<")
                }.start()
            }
        }

        var totalSnapshots = 0
        var inconsistentSnapshots = 0

        val writers = nodes.map { node ->
            Thread {
                sim.awaitSimulationStart()
                var i  = 0
                while (sim.isSimulating) {
                    node.write(node.nodeName(), i.toString())
                    i++
                    Thread.sleep(writerSleepMs)
                }
            }.apply { start() }
        }

        val monitor = Thread {
            sim.awaitSimulationStart()
            while (sim.isSimulating) {
                Thread.sleep(1000)

                val matrix = nodes.map { i ->
                    ids.map { key -> i.getLocalState(key) ?: "null" }
                }

                var snapshotConsistent = true
                val partitions = partitionGroups
                    ?.let { listOf(it.first, it.second) }
                    ?: listOf(ids.toSet())

                for (group in partitions) {
                    val rows = nodes.withIndex()
                        .filter { (_, node) -> node.nodeName() in group }
                        .map { (idx, _) -> matrix[idx] }
                    val groupConsistent = (0 until ids.size).all { col ->
                        rows.map { row -> row[col] }.toSet().size <= 1
                    }
                    if (!groupConsistent) snapshotConsistent = false
                }

                totalSnapshots += 1
                if (!snapshotConsistent) inconsistentSnapshots += 1

                println(if (snapshotConsistent) ANSI_GREEN else ANSI_RED)
                println("${variant.name} Snapshot $totalSnapshots - ${if (snapshotConsistent) "Consistent" else "Inconsistent"}")
                print("N/K"); ids.forEach { print("\t$it\t") }; println()
                matrix.forEachIndexed { i, row ->
                    print(nodes[i].nodeName()); row.forEach { print("\t$it${ if (it.length < 4) "\t" else "" }") }; println()
                }
                println(ANSI_RESET)
            }
        }.apply { start() }

        val simThread = Thread {
            sim.simulate(durationSec)
            sim.shutdown()
        }.apply { start() }

        writers.forEach { it.join() }
        monitor.join()
        simThread.join()

        return ExperimentStats(
            totalSnapshots,
            inconsistentSnapshots
        )
    }
}