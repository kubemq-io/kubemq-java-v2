package io.kubemq.sdk.queues;

@FunctionalInterface
interface RequestSender {
    void send(kubemq.Kubemq.QueuesDownstreamRequest request);
}