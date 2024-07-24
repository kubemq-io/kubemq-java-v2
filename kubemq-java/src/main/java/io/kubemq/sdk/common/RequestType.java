/*
 * MIT License
 *
 * Copyright (c) 2024 KubeMQ
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
 * Enum representing different types of requests that can be made to KubeMQ.
 */
@Getter
public enum RequestType {

    /**
     * Unknown request type.
     */
    RequestTypeUnknown(0),

    /**
     * Command request type.
     */
    Command(1),

    /**
     * Query request type.
     */
    Query(2);

    /**
     * The integer value associated with the request type.
     */
    private final int value;

    /**
     * Private constructor to associate an integer value with each request type.
     *
     * @param value the integer value representing the request type.
     */
    RequestType(int value) {
        this.value = value;
    }

}
