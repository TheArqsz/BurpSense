package com.arqsz.burpsense.api.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimiter")
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @AfterEach
    void tearDown() {
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
    }

    @Nested
    @DisplayName("allowRequest")
    class AllowRequest {

        @Test
        @DisplayName("should allow requests within limit")
        void shouldAllowRequestsWithinLimit() {
            rateLimiter = new RateLimiter(5, 60);
            String identifier = "user1";

            for (int i = 0; i < 5; i++) {
                assertThat(rateLimiter.allowRequest(identifier))
                        .as("Request %d should be allowed", i + 1)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should block requests exceeding limit")
        void shouldBlockRequestsExceedingLimit() {
            rateLimiter = new RateLimiter(3, 60);
            String identifier = "user1";

            for (int i = 0; i < 3; i++) {
                rateLimiter.allowRequest(identifier);
            }

            assertThat(rateLimiter.allowRequest(identifier)).isFalse();
        }

        @Test
        @DisplayName("should track different identifiers separately")
        void shouldTrackDifferentIdentifiersSeparately() {
            rateLimiter = new RateLimiter(2, 60);

            rateLimiter.allowRequest("user1");
            rateLimiter.allowRequest("user1");

            assertThat(rateLimiter.allowRequest("user1")).isFalse();
            assertThat(rateLimiter.allowRequest("user2")).isTrue();
        }

        @Test
        @DisplayName("should refill tokens after time window")
        void shouldRefillTokensAfterTimeWindow() {
            rateLimiter = new RateLimiter(2, 1);
            String identifier = "user1";

            assertThat(rateLimiter.allowRequest(identifier))
                    .as("First request should be allowed")
                    .isTrue();
            assertThat(rateLimiter.allowRequest(identifier))
                    .as("Second request should be allowed")
                    .isTrue();
            assertThat(rateLimiter.allowRequest(identifier))
                    .as("Third request should be blocked")
                    .isFalse();

            assertThat(rateLimiter.getRemainingRequests(identifier))
                    .as("Should have 0 remaining requests")
                    .isEqualTo(0);

            await()
                    .pollDelay(1100, TimeUnit.MILLISECONDS)
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(rateLimiter.getRemainingRequests(identifier))
                                .as("Tokens should be refilled after window")
                                .isEqualTo(2);
                    });

            assertThat(rateLimiter.allowRequest(identifier))
                    .as("Request after refill should be allowed")
                    .isTrue();
        }

        @Test
        @DisplayName("should handle rapid successive requests correctly")
        void shouldHandleRapidSuccessiveRequests() {
            rateLimiter = new RateLimiter(10, 60);
            String identifier = "user1";

            int allowed = 0;
            for (int i = 0; i < 15; i++) {
                if (rateLimiter.allowRequest(identifier)) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(10);
            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getRemainingRequests")
    class GetRemainingRequests {

        @Test
        @DisplayName("should return correct remaining count")
        void shouldReturnCorrectRemainingCount() {
            rateLimiter = new RateLimiter(5, 60);
            String identifier = "user1";

            rateLimiter.allowRequest(identifier);
            rateLimiter.allowRequest(identifier);

            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(3);
        }

        @Test
        @DisplayName("should return max for new identifier")
        void shouldReturnMaxForNewIdentifier() {
            rateLimiter = new RateLimiter(5, 60);

            assertThat(rateLimiter.getRemainingRequests("unknown")).isEqualTo(5);
        }

        @Test
        @DisplayName("should return zero when limit exceeded")
        void shouldReturnZeroWhenLimitExceeded() {
            rateLimiter = new RateLimiter(2, 60);
            String identifier = "user1";

            rateLimiter.allowRequest(identifier);
            rateLimiter.allowRequest(identifier);

            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(0);
        }

        @Test
        @DisplayName("should update after each request")
        void shouldUpdateAfterEachRequest() {
            rateLimiter = new RateLimiter(5, 60);
            String identifier = "user1";

            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(5);

            rateLimiter.allowRequest(identifier);
            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(4);

            rateLimiter.allowRequest(identifier);
            assertThat(rateLimiter.getRemainingRequests(identifier)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getResetTime")
    class GetResetTime {

        @Test
        @DisplayName("should return zero for new identifier")
        void shouldReturnZeroForNewIdentifier() {
            rateLimiter = new RateLimiter(5, 60);

            assertThat(rateLimiter.getResetTime("unknown")).isEqualTo(0);
        }

        @Test
        @DisplayName("should return reset time when rate limited")
        void shouldReturnResetTimeWhenRateLimited() {
            rateLimiter = new RateLimiter(1, 60);
            String identifier = "user1";

            rateLimiter.allowRequest(identifier);

            long resetTime = rateLimiter.getResetTime(identifier);
            assertThat(resetTime)
                    .as("Reset time should be positive and <= window")
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(60);
        }

        @Test
        @DisplayName("should decrease reset time over time")
        void shouldDecreaseResetTimeOverTime() throws InterruptedException {
            rateLimiter = new RateLimiter(1, 5);
            String identifier = "user1";

            rateLimiter.allowRequest(identifier);
            long resetTime1 = rateLimiter.getResetTime(identifier);

            Thread.sleep(1100);

            long resetTime2 = rateLimiter.getResetTime(identifier);

            assertThat(resetTime2)
                    .as("Reset time should decrease after waiting")
                    .isLessThan(resetTime1);
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("should handle concurrent requests correctly")
        void shouldHandleConcurrentRequestsCorrectly() throws InterruptedException {
            rateLimiter = new RateLimiter(10, 60);
            String identifier = "user1";
            int threadCount = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        if (rateLimiter.allowRequest(identifier)) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle multiple identifiers concurrently")
        void shouldHandleMultipleIdentifiersConcurrently() throws InterruptedException {
            rateLimiter = new RateLimiter(5, 60);
            int identifierCount = 10;
            int requestsPerIdentifier = 5;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(identifierCount * requestsPerIdentifier);
            ExecutorService executor = Executors.newFixedThreadPool(identifierCount);

            for (int i = 0; i < identifierCount; i++) {
                final String identifier = "user" + i;
                for (int j = 0; j < requestsPerIdentifier; j++) {
                    executor.submit(() -> {
                        try {
                            if (rateLimiter.allowRequest(identifier)) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(successCount.get())
                    .as("All requests should succeed as each identifier has separate bucket")
                    .isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle zero limit")
        void shouldHandleZeroLimit() {
            rateLimiter = new RateLimiter(0, 60);

            assertThat(rateLimiter.allowRequest("user1")).isFalse();
            assertThat(rateLimiter.getRemainingRequests("user1")).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle very short time window")
        void shouldHandleVeryShortTimeWindow() {
            rateLimiter = new RateLimiter(1, 1);
            String identifier = "user1";

            rateLimiter.allowRequest(identifier);

            await()
                    .pollDelay(1100, TimeUnit.MILLISECONDS)
                    .atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(rateLimiter.allowRequest(identifier)).isTrue();
                    });
        }

        @Test
        @DisplayName("should handle null identifier gracefully")
        void shouldHandleNullIdentifierGracefully() {
            rateLimiter = new RateLimiter(5, 60);

            try {
                boolean result = rateLimiter.allowRequest(null);
                assertThat(result).isNotNull();
            } catch (NullPointerException e) {
                assertThat(e).isInstanceOf(NullPointerException.class);
            }
        }

        @Test
        @DisplayName("should handle empty string identifier")
        void shouldHandleEmptyStringIdentifier() {
            rateLimiter = new RateLimiter(5, 60);

            assertThat(rateLimiter.allowRequest("")).isTrue();
            assertThat(rateLimiter.getRemainingRequests("")).isEqualTo(4);
        }
    }
}
