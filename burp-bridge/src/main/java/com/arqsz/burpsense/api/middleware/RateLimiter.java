package com.arqsz.burpsense.api.middleware;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token bucket rate limiter for preventing brute force attacks
 */
public class RateLimiter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowSeconds;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a rate limiter
     * 
     * @param maxRequests   Maximum requests allowed per window
     * @param windowSeconds Time window in seconds
     */
    public RateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanup,
                5, 5, TimeUnit.MINUTES);
    }

    /**
     * Checks if a request from the given identifier is allowed
     * 
     * @param identifier Unique identifier (IP address, user ID, etc.)
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String identifier) {
        TokenBucket bucket = buckets.computeIfAbsent(
                identifier,
                k -> new TokenBucket(maxRequests, windowSeconds));
        return bucket.tryConsume();
    }

    /**
     * Gets remaining requests for an identifier
     * 
     * @param identifier The identifier to check
     * @return Number of remaining requests in current window
     */
    public int getRemainingRequests(String identifier) {
        TokenBucket bucket = buckets.get(identifier);
        return bucket == null ? maxRequests : bucket.getAvailableTokens();
    }

    /**
     * Gets seconds until rate limit resets for an identifier
     * 
     * @param identifier The identifier to check
     * @return Seconds until reset, or 0 if not rate limited
     */
    public long getResetTime(String identifier) {
        TokenBucket bucket = buckets.get(identifier);
        return bucket == null ? 0 : bucket.getSecondsUntilReset();
    }

    /**
     * Removes buckets that haven't been used in over 1 hour
     */
    private void cleanup() {
        long cutoff = Instant.now().getEpochSecond() - 3600;
        buckets.entrySet().removeIf(entry -> entry.getValue().getLastAccessTime() < cutoff);
    }

    /**
     * Shuts down the cleanup executor
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
    }

    /**
     * Token bucket for rate limiting
     */
    private static class TokenBucket {
        private final int capacity;
        private final long windowSeconds;
        private int tokens;
        private long lastRefillTime;
        private long lastAccessTime;

        public TokenBucket(int capacity, long windowSeconds) {
            this.capacity = capacity;
            this.windowSeconds = windowSeconds;
            this.tokens = capacity;
            this.lastRefillTime = Instant.now().getEpochSecond();
            this.lastAccessTime = lastRefillTime;
        }

        public synchronized boolean tryConsume() {
            refill();
            lastAccessTime = Instant.now().getEpochSecond();

            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        public synchronized int getAvailableTokens() {
            refill();
            return tokens;
        }

        public synchronized long getSecondsUntilReset() {
            long now = Instant.now().getEpochSecond();
            long elapsed = now - lastRefillTime;
            return Math.max(0, windowSeconds - elapsed);
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        private void refill() {
            long now = Instant.now().getEpochSecond();
            long elapsed = now - lastRefillTime;

            if (elapsed >= windowSeconds) {
                tokens = capacity;
                lastRefillTime = now;
            }
        }
    }
}
