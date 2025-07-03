# sim4da

**A Java-based framework for simulating distributed algorithms.**

## Motivation

Distributed algorithms are notoriously difficult to prototype and test due to the complexity of networking, threading, and message handling. **sim4da** abstracts away these concerns, letting you concentrate on your algorithm’s logic. With minimal boilerplate, you can quickly spin up nodes, pass messages, and visualize behavior in a controlled simulation.

## Core Concepts

- **IS-A Node**  
  Extend the `org.oxoo2a.sim4da.Node` base class. The framework handles thread creation, message routing, and logging. Override the `engage()` method to implement your node’s algorithmic steps, using helper methods like `send()`, `broadcast()`, and `receive()`.

- **HAS-A NetworkConnection**  
  For scenarios where inheritance isn’t ideal, instantiate and manage a `NetworkConnection` directly:
  ```java
  public class CustomAgent {
      private final NetworkConnection nc = new NetworkConnection("Agent1");

      public CustomAgent() {
          nc.engage(this::runLogic);
      }

      private void runLogic() {
          // send, receive, process messages
      }
  }
  ```

## Quick Start

### Example: Token Ring with IS-A Node

```java
public class RingNode extends Node {
    public RingNode(String name) {
        super(name);
    }

    @Override
    protected void engage() {
        // send, receive, process token passing
    }
}
```

### Example: Custom Agent with HAS-A NetworkConnection

```java
public class Agent {
    private final NetworkConnection nc = new NetworkConnection("AgentX");

    public Agent() {
        nc.engage(this::main);
    }

    private void main() {
        Message msg = nc.receive();
        // algorithm logic
    }
}
```

## Logging Configuration

By default, a “hidden” `logback.xml` on the classpath configures **DEBUG**-level logging for all network activity and node operations. To customize:

1. Create your own `logback.xml` in `src/main/resources/`.
2. Define desired log levels, appenders, and formats.
3. The simulator will automatically pick up your configuration instead of the default.

## Further Reading

- See `OneRingToRuleThemAllTest.java` for a complete token-passing simulation example.
- Review Javadoc comments in `Node.java` and `NetworkConnection.java` for detailed API guidance.
