package com.arqsz.burpsense.api.middleware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fixed window rate limiter for preventing brute force attacks
 */
public class RateLimiter {

    private final Map<String, FixedWindow> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * Creates a rate limiter
     * 
     * @param maxRequests   Maximum requests allowed per window
     * @param windowSeconds Time window in seconds
     */
    public RateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = TimeUnit.SECONDS.toMillis(windowMillis);

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
        FixedWindow bucket = windows.computeIfAbsent(
                identifier,
                k -> new FixedWindow(maxRequests, windowMillis));
        return bucket.tryConsume();
    }

    /**
     * Gets remaining requests for an identifier
     * 
     * @param identifier The identifier to check
     * @return Number of remaining requests in current window
     */
    public int getRemainingRequests(String identifier) {
        FixedWindow window = windows.get(identifier);
        return window == null ? maxRequests : window.getAvailableTokens();
    }

    /**
     * Gets seconds until rate limit resets for an identifier
     * 
     * @param identifier The identifier to check
     * @return Seconds until reset, or 0 if not rate limited
     */
    public long getResetTime(String identifier) {
        FixedWindow window = windows.get(identifier);
        return window == null ? 0 : window.getSecondsUntilReset();
    }

    /**
     * Removes windows that haven't been used in over 1 hour
     */
    private void cleanup() {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        windows.entrySet().removeIf(entry -> entry.getValue().getLastAccessTime() < cutoff);
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
    private static class FixedWindow {
        private final int capacity;
        private final long windowMillis;
        private int tokens;
        private long lastRefillTime;
        private long lastAccessTime;

        public FixedWindow(int capacity, long windowMillis) {
            this.capacity = capacity;
            this.windowMillis = windowMillis;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
            this.lastAccessTime = lastRefillTime;
        }

        public synchronized boolean tryConsume() {
            refill();
            lastAccessTime = System.currentTimeMillis();

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
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            return Math.max(0, (windowMillis - elapsed) / 1000);
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;

            if (elapsed >= windowMillis) {
                tokens = capacity;
                lastRefillTime = now;
            }
        }
    }
}
