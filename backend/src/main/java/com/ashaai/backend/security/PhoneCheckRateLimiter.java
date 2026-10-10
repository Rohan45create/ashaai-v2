package com.ashaai.backend.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter for the public worker phone check endpoint.
 * Protects against phone enumeration attacks and SMS spamming/costs without adding external dependencies.
 */
@Component
public class PhoneCheckRateLimiter {

    public static final int IP_LIMIT = 5;       // max 5 requests per minute per IP
    public static final int PHONE_LIMIT = 3;    // max 3 requests per minute per Phone
    public static final long WINDOW_MILLIS = 60_000L; // 60 seconds window

    private final Map<String, Deque<Long>> ipTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> phoneTimestamps = new ConcurrentHashMap<>();

    public boolean tryAcquireIp(String ip) {
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        return tryAcquire(ipTimestamps, "ip:" + ip, IP_LIMIT);
    }

    public boolean tryAcquirePhone(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return true;
        }
        return tryAcquire(phoneTimestamps, "phone:" + normalizedPhone, PHONE_LIMIT);
    }

    private boolean tryAcquire(Map<String, Deque<Long>> map, String key, int limit) {
        long now = Instant.now().toEpochMilli();
        long cutoff = now - WINDOW_MILLIS;

        Deque<Long> deque = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /**
     * Resets rate limiter state. Used in unit tests.
     */
    public void resetForTesting() {
        ipTimestamps.clear();
        phoneTimestamps.clear();
    }
}
