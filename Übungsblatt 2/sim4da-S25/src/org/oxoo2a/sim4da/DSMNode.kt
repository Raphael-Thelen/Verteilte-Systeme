package org.oxoo2a.sim4da

interface DSMNode {
    fun write(key: String, value: String)
    fun read(key: String): String?
    fun nodeName(): String // monitoring for testing reasons
    fun getLocalState(key: String): String?
}