package com.ticketbooking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisLockService {

    private static final String SEAT_HOLD_PREFIX = "seat_hold:";
    private static final String HOLD_OWNER_PREFIX = "hold_owner:";

    private static final RedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end",
            Long.class
    );

    private static final RedisScript<Long> ATOMIC_MULTI_ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "for i, key in ipairs(KEYS) do " +
            "    if redis.call('exists', key) == 1 then " +
            "        return 0 " +
            "    end " +
            "end " +
            "for i, key in ipairs(KEYS) do " +
            "    redis.call('set', key, ARGV[1], 'ex', ARGV[2]) " +
            "end " +
            "return 1",
            Long.class
    );

    private static final RedisScript<Long> PUBLISH_AVAILABLE_IF_UNHELD_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "    redis.call('publish', ARGV[1], ARGV[2]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end",
            Long.class
    );

    private static final RedisScript<Long> RELEASE_HOLD_WITH_OWNER_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    redis.call('del', KEYS[1]) " +
            "    redis.call('del', KEYS[2]) " +
            "    if ARGV[2] and ARGV[2] ~= '' and ARGV[3] and ARGV[3] ~= '' then " +
            "        redis.call('publish', ARGV[2], ARGV[3]) " +
            "    end " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final long holdTtlSeconds;

    public RedisLockService(
            StringRedisTemplate redisTemplate,
            @Value("${ticketbooking.hold.ttl-seconds:300}") long holdTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    public boolean acquireHold(Long showtimeId, Long seatId, String token) {
        return acquireHold(showtimeId, seatId, token, this.holdTtlSeconds);
    }

    public boolean acquireHold(Long showtimeId, Long seatId, String token, long ttlSeconds) {
        validateSingleParameters(showtimeId, seatId, token);
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL seconds must be greater than 0");
        }

        String key = buildSeatHoldKey(showtimeId, seatId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(ttlSeconds));

        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseHold(Long showtimeId, Long seatId, String token) {
        validateSingleParameters(showtimeId, seatId, token);
        String key = buildSeatHoldKey(showtimeId, seatId);

        Long result = redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(key),
                token
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean validateHoldOwnership(Long showtimeId, Long seatId, String token) {
        validateSingleParameters(showtimeId, seatId, token);
        String key = buildSeatHoldKey(showtimeId, seatId);
        String currentOwnerToken = redisTemplate.opsForValue().get(key);

        return token.equals(currentOwnerToken);
    }

    public boolean acquireMultiHold(Long showtimeId, Collection<Long> seatIds, String token) {
        return acquireMultiHold(showtimeId, seatIds, token, this.holdTtlSeconds);
    }

    public boolean acquireMultiHold(Long showtimeId, Collection<Long> seatIds, String token, long ttlSeconds) {
        validateMultiParameters(showtimeId, seatIds, token);
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL seconds must be greater than 0");
        }

        List<String> keys = seatIds.stream()
                .map(seatId -> buildSeatHoldKey(showtimeId, seatId))
                .toList();

        Long result = redisTemplate.execute(
                ATOMIC_MULTI_ACQUIRE_SCRIPT,
                keys,
                token,
                String.valueOf(ttlSeconds)
        );

        return Long.valueOf(1L).equals(result);
    }

    public String buildSeatHoldKey(Long showtimeId, Long seatId) {
        return SEAT_HOLD_PREFIX + showtimeId + ":" + seatId;
    }

    public boolean isSeatHeld(Long showtimeId, Long seatId) {
        validateSingleParameters(showtimeId, seatId, "internal-probe");
        String key = buildSeatHoldKey(showtimeId, seatId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public long getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    public String getHoldToken(Long showtimeId, Long seatId) {
        if (showtimeId == null || seatId == null) {
            return null;
        }
        String key = buildSeatHoldKey(showtimeId, seatId);
        return redisTemplate.opsForValue().get(key);
    }

    public Long getHoldRemainingTtl(Long showtimeId, Long seatId) {
        if (showtimeId == null || seatId == null) {
            return null;
        }
        String key = buildSeatHoldKey(showtimeId, seatId);
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public boolean publishAvailableIfUnheld(Long showtimeId, Long seatId, String channel, String messageJson) {
        if (showtimeId == null || seatId == null || channel == null || messageJson == null) {
            return false;
        }
        String key = buildSeatHoldKey(showtimeId, seatId);
        Long result = redisTemplate.execute(
                PUBLISH_AVAILABLE_IF_UNHELD_SCRIPT,
                Collections.singletonList(key),
                channel,
                messageJson
        );
        return Long.valueOf(1L).equals(result);
    }

    public String buildHoldOwnerKey(String holdToken) {
        return HOLD_OWNER_PREFIX + holdToken;
    }

    public boolean releaseHoldWithOwner(Long showtimeId, Long seatId, String expectedToken, String channel, String messageJson) {
        if (showtimeId == null || seatId == null || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        String seatHoldKey = buildSeatHoldKey(showtimeId, seatId);
        String holdOwnerKey = buildHoldOwnerKey(expectedToken);

        List<String> keys = List.of(seatHoldKey, holdOwnerKey);
        Long result = redisTemplate.execute(
                RELEASE_HOLD_WITH_OWNER_SCRIPT,
                keys,
                expectedToken,
                channel != null ? channel : "",
                messageJson != null ? messageJson : ""
        );
        return Long.valueOf(1L).equals(result);
    }

    private void validateSingleParameters(Long showtimeId, Long seatId, String token) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (seatId == null) {
            throw new IllegalArgumentException("Seat ID must not be null");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Hold token must not be null or blank");
        }
    }

    private void validateMultiParameters(Long showtimeId, Collection<Long> seatIds, String token) {
        if (showtimeId == null) {
            throw new IllegalArgumentException("Showtime ID must not be null");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Seat IDs collection must not be null or empty");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Hold token must not be null or blank");
        }

        Set<Long> uniqueIds = new HashSet<>();
        for (Long seatId : seatIds) {
            if (seatId == null) {
                throw new IllegalArgumentException("Seat ID inside collection must not be null");
            }
            if (!uniqueIds.add(seatId)) {
                throw new IllegalArgumentException("Duplicate seat ID detected in hold request: " + seatId);
            }
        }
    }
}
