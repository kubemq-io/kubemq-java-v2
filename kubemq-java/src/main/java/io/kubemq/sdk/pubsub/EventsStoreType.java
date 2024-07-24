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
package io.kubemq.sdk.pubsub;

import lombok.Getter;

/**
 * Enum representing different types of event store subscriptions.
 * These types define the starting point from which events are stored and retrieved.
 */
@Getter
public enum EventsStoreType {

    /**
     * Undefined event store type.
     * This is the default value and should be explicitly set to a valid type before use.
     */
    Undefined(0),

    /**
     * Start storing events from the point when the subscription is made.
     */
    StartNewOnly(1),

    /**
     * Start storing events from the first event available.
     */
    StartFromFirst(2),

    /**
     * Start storing events from the last event available.
     */
    StartFromLast(3),

    /**
     * Start storing events from a specific sequence number.
     */
    StartAtSequence(4),

    /**
     * Start storing events from a specific point in time.
     */
    StartAtTime(5),

    /**
     * Start storing events from a specific time delta (e.g., last 30 minutes).
     */
    StartAtTimeDelta(6);

    private final int value;

    /**
     * Constructor for the enum, assigning a specific integer value to each type.
     *
     * @param value The integer value representing the event store type.
     */
     EventsStoreType(int value) {
        this.value = value;
    }

    /**
     * Gets the integer value associated with the event store type.
     *
     * @return The integer value of the event store type.
     */
    public int getValue() {
        return value;
    }
}
