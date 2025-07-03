package org.oxoo2a.sim4da;

import java.lang.reflect.Constructor;

public abstract class Message {

    private String sender;

    protected Message() {
        this.sender = "Unknown";
    }

    // Copy constructor for base fields
    protected Message(Message original) {
        this.sender = original.sender;
    }

    protected void setSender(String sender) {
        this.sender = sender;
    }

    public String getSender() {
        return sender;
    }

    public Message copy() {
        try {
            Constructor<? extends Message> ctor =
                    this.getClass().getDeclaredConstructor(this.getClass());
            ctor.setAccessible(true);
            return ctor.newInstance(this);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Subclass ("+this.getClass()+") must define a proper copy constructor.", e);
        }
    }
}