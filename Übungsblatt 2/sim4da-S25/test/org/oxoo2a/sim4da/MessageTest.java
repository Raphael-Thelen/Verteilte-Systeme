package org.oxoo2a.sim4da;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    static class Token extends Message {
        public final String token;

        public Token(String token) {
            super();
            this.token = token;
        }

        // Copy constructor for base fields
        public Token(Token original) {
            super(original);
            this.token = original.token;
        }
    }

    static class ValueToken extends Token {
        public int value;

        public ValueToken(String token, int value) {
            super(token);
            this.value = value;
        }

        // Copy constructor for base fields
        public ValueToken(ValueToken original) {
            super(original);
            this.value = original.value;
        }
    }

    @BeforeAll
    static void setUp() {
    }

    @Test
    void testMessageBasics() {
        Token token = new Token("token");
        assertEquals("token", token.token);
        assertEquals("Unknown", token.getSender());

        ValueToken value = new ValueToken("value", 42);
        assertEquals("value", value.token);
        assertEquals(42, value.value);

        Message m = value.copy();
        System.out.println("m of class " + m.getClass());
        assertInstanceOf(ValueToken.class, m);
        assertEquals("value", ((ValueToken) m).token);
        assertEquals(42, ((ValueToken) m).value);
    };

}