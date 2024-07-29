/*
 * MIT License
 *
 * Copyright (c) 2018 KubeMQ
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.kubemq.sdk.common;

import lombok.Getter;

/**
 * Enumeration of the types of subscription operations supported by the system.
 * Each type represents a different pattern of subscription behavior.
 */
@Getter
public enum SubscribeType {

    /**
     * Undefined subscription type. Used as a default value when the type is not specified.
     */
    SubscribeTypeUndefined(0),

    /**
     * Subscription for PubSub events. Represents a standard PubSub event subscription.
     */
    Events(1),

    /**
     * Subscription for PubSub events with persistence. Represents events that are stored and can be retrieved later.
     */
    EventsStore(2),

    /**
     * Subscription for performing actions in a request-reply pattern. Represents commands that require an action.
     */
    Commands(3),

    /**
     * Subscription for receiving data in a request-reply pattern. Represents queries that return data.
     */
    Queries(4);

    private final int value;

    /**
     * Constructs a {@code SubscribeType} with the specified value.
     *
     * @param value The integer value associated with the subscription type.
     */
    SubscribeType(int value) {
        this.value = value;
    }

    /**
     * Returns the integer value associated with this subscription type.
     *
     * @return The integer value of the subscription type.
     */
    public int getValue() {
        return value;
    }
}
