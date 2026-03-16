package io.kubemq.sdk.queues;

@FunctionalInterface
public interface RequestSender {
  /**
   * Sends the .
   *
   * @param request the request
   */
  void send(kubemq.Kubemq.QueuesDownstreamRequest request);
}
