package org.oxoo2a.sim4da;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a network connection for a simulation node.
 * Manages sending, receiving messages, and thread lifecycle.
 */
public class NetworkConnection {


    /**
     * Initializes the network connection for a node.
     *
     * @param node_name the unique identifier of the node.
     */
    public NetworkConnection(String node_name ) {
        this.node_name = node_name;
        logger = LoggerFactory.getLogger(node_name);
        peer = new NodeProxy(this);
        network.registerConnection(this,peer);
    }

    /**
     * Retrieves the name of this node's connection.
     *
     * @return the node name.
     */
    public String NodeName () {
        return node_name;
    }

    /**
     * Starts the node's main thread after simulation start.
     *
     * @param node_main the Runnable containing node logic to execute.
     */
    public void engage ( Runnable node_main ) {
        this.node_main = node_main;
        thread = new Thread(this::node_main_base);
        thread.start();
    }

    private void node_main_base() {
        simulator.awaitSimulationStart();
        if (simulator.isSimulating())
            node_main.run();
    }

    /**
     * Waits for the node's main thread to finish execution.
     */
    public void join () {
        try {
            thread.join();
        }
        catch (InterruptedException e) {}
    }

    /**
     * Receives the next message from the network.
     *
     * @return the received Message.
     */
    public Message receive () {
        Message m = network.receive(this);
        logger.debug("Received message from "+m.getSender());
        return m;
    }

    /**
     * Sends a message to a specified node with error reporting.
     *
     * @param message the Message to send.
     * @param to_node_name the recipient node's name.
     * @throws UnknownNodeException if the target node is not registered.
     */
    public void send ( Message message, String to_node_name ) throws UnknownNodeException {
        logger.debug("Sending message to "+to_node_name);
        network.send(message, this, to_node_name);
    }

    /**
     * Sends a message to a node, suppressing unknown node errors.
     *
     * @param message the Message to send.
     * @param to_node_name the recipient node's name.
     */
    public void sendBlindly ( Message message, String to_node_name ) {
        try {
            send(message, to_node_name);
        }
        catch (Exception e) {}
    }

    /**
     * Broadcasts a message to all nodes in the network.
     *
     * @param message the Message to broadcast.
     */
    public void send ( Message message ) {
        logger.debug("Broadcasting message");
        network.send(message, this);
    }

    /**
     * Provides access to the logger for this connection.
     *
     * @return the SLF4J Logger instance.
     */
    public Logger getLogger() {
        return logger;
    }

    private final String node_name;
    private final Simulator simulator = Simulator.getInstance();
    private final Network network = Network.getInstance();
    private Thread thread = null;
    private final NodeProxy peer;
    private final Logger logger;
    private Runnable node_main = null;

    /**
     * Interrupts the node's main execution thread.
     */
    public void interrupt() {
        thread.interrupt();
    }
}
