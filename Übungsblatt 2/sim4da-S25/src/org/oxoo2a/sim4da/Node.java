package org.oxoo2a.sim4da;

/**
 * Primary base class for simulating a node in a distributed algorithm.
 * Sets up networking and provides basic messaging operations.
 */
public class Node {

    /**
     * Constructs a Node with the given name, initializing its network connection.
     *
     * @param name the unique identifier for this node in the network.
     */
    public Node ( String name ) {
        nc = new NetworkConnection(name);
        nc.engage(this::engage);
    }

    /**
     * Called when the network connection engages this node.
     * Default implementation logs a debug message; override to define custom behavior.
     */
    protected void engage () {
        nc.getLogger().debug("Engaging node, but no code defined");
    }

    /**
     * Sends a message to a specific node, with error handling.
     *
     * @param message the Message object to send.
     * @param to_node_name the name of the recipient node.
     * @throws UnknownNodeException if the destination node is not recognized.
     */
    protected void send ( Message message, String to_node_name ) throws UnknownNodeException {
        nc.send(message, to_node_name);
    }

    /**
     * Sends a message to a specific node without throwing if the node is unknown.
     *
     * @param message the Message object to send.
     * @param to_node_name the name of the recipient node.
     */
    protected void sendBlindly ( Message message, String to_node_name ) {
        nc.sendBlindly(message, to_node_name);
    }


    /**
     * Broadcasts a message to all connected nodes.
     *
     * @param message the Message object to broadcast.
     */
    protected void broadcast ( Message message ) {
        nc.send(message);
    }

    /**
     * Receives the next message from the network. Blocks until a message is available.
     *
     * @return the received Message, or null in case of an error (mostly InterruptedException).
     */
    protected Message receive () {
        return nc.receive();
    }

    /**
     * Retrieves this node's unique name.
     *
     * @return the name of this node.
     */
    protected String NodeName () {
        return nc.NodeName();
    }

    /**
     * Pauses execution for the specified duration.
     *
     * @param millis the time to sleep in milliseconds.
     */
    protected void sleep ( int millis ) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }
    private NetworkConnection nc = null;
}
