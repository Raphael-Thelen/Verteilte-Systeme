package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test demonstrating a simple token-passing ring simulation.
 * Shows how to implement a coordinator and ring segments using the sim4da framework.
 */
public class OneRingToRuleThemAllTest {

    /**
     * Message type representing a token carrying an integer value around the ring.
     */
    static class Token extends Message {
        /**
         * Creates a new Token with initial value zero.
         */
        public int value;

        public Token() {
            super();
            this.value = 0;
        }

        /**
         * Copy constructor for Token, preserving original message metadata.
         *
         * @param original the Token to copy.
         */
        private Token(Token original) {
            super(original);
            this.value = original.value;
        }
    }

    /**
     * Message type signaling the end of the token-passing simulation.
     */
    static class EndMessage extends Message {
        /**
         * Creates a new EndMessage.
         */
        public EndMessage() {}
        /**
         * Copy constructor for EndMessage, preserving original message metadata.
         *
         * @param original the EndMessage to copy.
         */
        private EndMessage(EndMessage original) {
            super(original);
        }
    }

    /**
     * Coordinator node that initiates the token and sends termination signal.
     */
    class Coordinator {
        /**
         * Constructs a Coordinator with specified wait time before termination.
         *
         * @param waitTime time in milliseconds to wait before sending end message.
         */
        public Coordinator( int waitTime) {
            this.waitTime = waitTime;
        }

        /**
         * Starts the coordinator's engagement, scheduling its main logic.
         */
        public void engage () {
            nc.engage(this::start);
        }

        /**
         * Main logic for coordinator: sends initial token, waits, then sends end message.
         */
        private void start() {
            Token t = new Token();
            nc.sendBlindly(t, "0");
            try {
                Thread.sleep(waitTime);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            EndMessage e = new EndMessage();
            nc.sendBlindly(e, "0");
        }

        /**
         * Network connection used by the coordinator to send messages.
         */
        private final NetworkConnection nc = new NetworkConnection("Coordinator");
        private final int waitTime;
    }

    /**
     * Node representing a segment in the token ring.
     * Receives, processes, and forwards the token or end messages.
     */
    static class RingSegment extends Node {
        /**
         * Constructs a RingSegment with identifiers for itself and the next node.
         *
         * @param id numeric identifier of this ring segment.
         * @param next_id numeric identifier of the next ring segment.
         */
        public RingSegment(int id, int next_id) {
            super(String.valueOf(id));
            this.id = String.valueOf(id);
            this.next_id = String.valueOf(next_id);
        }

        /**
         * Overrides Node.engage to implement token-processing loop.
         * Receives Token or EndMessage and forwards appropriately.
         */
        @Override
        public void engage() {
            boolean running = true;
            while (running) {
                Message received = receive();
                switch (received) {
                    case Token t -> {
                        System.out.printf("Ring segment %s received token from %s%n", NodeName(), t.getSender());
                        sleep(500);
                        int v = t.value;
                        System.out.printf("Ring segment %s received token %d\n", NodeName(), v);
                        t.value++;
                        sendBlindly(t, next_id);
                    }
                    case EndMessage e -> {
                        System.out.printf("Ring segment %s received end message; terminating.\n", NodeName());
                        sendBlindly(e, next_id);
                        running = false;
                    }
                    default -> {
                        throw new IllegalStateException("Unexpected message: " + received.getClass());
                    }
                }
            }
        }

        private final String id;
        private final String next_id;
    }

    /**
     * Unit test that sets up a ring of segments and a coordinator, runs the simulation,
     * and verifies that the ring completes without errors.
     */
    @Test
    void testOneRingToRuleThemAll() {
        final int ringSize = 5;
        Simulator simulator = Simulator.getInstance();
        RingSegment[] segments = new RingSegment[ringSize];
        for (int i = 0; i < ringSize; i++) {
            segments[i] = new RingSegment(i, (i+1) % ringSize);
        }
        Coordinator coordinator = new Coordinator(5000);
        coordinator.engage();

        simulator.simulate();
        simulator.shutdown();
    }
}
