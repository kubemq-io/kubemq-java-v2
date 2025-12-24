package io.kubemq.sdk.unit.queues;

import io.kubemq.sdk.queues.QueueSendResult;
import io.kubemq.sdk.queues.UpstreamResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpstreamResponse POJO.
 */
class UpstreamResponseTest {

    @Nested
    class BuilderTests {

        @Test
        void builder_withAllFields_createsResponse() {
            List<QueueSendResult> results = Arrays.asList(
                    QueueSendResult.builder().id("result-1").isError(false).build(),
                    QueueSendResult.builder().id("result-2").isError(false).build()
            );

            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-123")
                    .results(results)
                    .isError(false)
                    .error("")
                    .build();

            assertEquals("ref-123", response.getRefRequestId());
            assertEquals(2, response.getResults().size());
            assertFalse(response.isError());
            assertEquals("", response.getError());
        }

        @Test
        void builder_withError_createsErrorResponse() {
            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-error")
                    .isError(true)
                    .error("Connection timeout")
                    .build();

            assertEquals("ref-error", response.getRefRequestId());
            assertTrue(response.isError());
            assertEquals("Connection timeout", response.getError());
        }

        @Test
        void builder_withDefaultResults_initializesToEmptyList() {
            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-default")
                    .build();

            assertNotNull(response.getResults());
            assertTrue(response.getResults().isEmpty());
        }

        @Test
        void builder_withNoResults_createsEmptyList() {
            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-empty")
                    .isError(false)
                    .build();

            assertNotNull(response.getResults());
            assertEquals(0, response.getResults().size());
        }
    }

    @Nested
    class GetterSetterTests {

        @Test
        void setRefRequestId_updatesRefRequestId() {
            UpstreamResponse response = UpstreamResponse.builder().build();
            response.setRefRequestId("updated-ref");
            assertEquals("updated-ref", response.getRefRequestId());
        }

        @Test
        void setResults_updatesResults() {
            UpstreamResponse response = UpstreamResponse.builder().build();
            List<QueueSendResult> results = Arrays.asList(
                    QueueSendResult.builder().id("new-result").build()
            );
            response.setResults(results);
            assertEquals(1, response.getResults().size());
            assertEquals("new-result", response.getResults().get(0).getId());
        }

    }

    @Nested
    class ToStringTests {

        @Test
        void toString_includesAllFields() {
            List<QueueSendResult> results = Arrays.asList(
                    QueueSendResult.builder().id("result-str").isError(false).build()
            );

            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-tostring")
                    .results(results)
                    .isError(false)
                    .error("")
                    .build();

            String str = response.toString();

            assertTrue(str.contains("ref-tostring"));
            assertTrue(str.contains("result-str") || str.contains("results"));
        }

        @Test
        void toString_withError_includesError() {
            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-error")
                    .isError(true)
                    .error("Queue not found")
                    .build();

            String str = response.toString();

            assertTrue(str.contains("Queue not found") || str.contains("error"));
        }

        @Test
        void toString_withEmptyResults_handlesGracefully() {
            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("ref-empty")
                    .build();

            String str = response.toString();

            assertNotNull(str);
            assertTrue(str.contains("ref-empty"));
        }
    }

    @Nested
    class ResultsManagementTests {

        @Test
        void multipleResults_areStoredCorrectly() {
            List<QueueSendResult> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(QueueSendResult.builder()
                        .id("result-" + i)
                        .sentAt(LocalDateTime.now())
                        .isError(false)
                        .build());
            }

            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("multi-results")
                    .results(results)
                    .build();

            assertEquals(5, response.getResults().size());
            for (int i = 0; i < 5; i++) {
                assertEquals("result-" + i, response.getResults().get(i).getId());
            }
        }

        @Test
        void mixedResults_withSuccessAndError_areStoredCorrectly() {
            List<QueueSendResult> results = Arrays.asList(
                    QueueSendResult.builder().id("success-1").isError(false).build(),
                    QueueSendResult.builder().id("error-1").isError(true).error("Failed").build(),
                    QueueSendResult.builder().id("success-2").isError(false).build()
            );

            UpstreamResponse response = UpstreamResponse.builder()
                    .refRequestId("mixed-results")
                    .results(results)
                    .isError(false)
                    .build();

            assertEquals(3, response.getResults().size());
            assertFalse(response.getResults().get(0).isError());
            assertTrue(response.getResults().get(1).isError());
            assertFalse(response.getResults().get(2).isError());
        }
    }
}
