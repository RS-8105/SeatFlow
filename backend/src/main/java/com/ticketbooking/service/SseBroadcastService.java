package com.ticketbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketbooking.config.RedisPubSubConfig;
import com.ticketbooking.dto.SeatStateEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseBroadcastService implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcastService.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> showtimeEmitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ChannelTopic seatEventsTopic;
    private final RedisLockService redisLockService;
    private final ObjectMapper objectMapper;

    public SseBroadcastService(
            StringRedisTemplate redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ChannelTopic seatEventsTopic,
            RedisLockService redisLockService
    ) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.seatEventsTopic = seatEventsTopic;
        this.redisLockService = redisLockService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        listenerContainer.addMessageListener(this, seatEventsTopic);
        log.info("Subscribed SseBroadcastService to Redis topic: {}", seatEventsTopic.getTopic());
    }

    public SseEmitter subscribe(Long showtimeId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> removeEmitter(showtimeId, emitter));
        emitter.onTimeout(() -> removeEmitter(showtimeId, emitter));
        emitter.onError(e -> removeEmitter(showtimeId, emitter));

        showtimeEmitters.computeIfAbsent(showtimeId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "showtimeId", showtimeId,
                            "message", "Connected to live seat updates stream."
                    )));
        } catch (IOException e) {
            removeEmitter(showtimeId, emitter);
        }

        return emitter;
    }

    public void publishSeatEvent(Long showtimeId, Long seatId, String status, Instant expiresAt) {
        try {
            SeatStateEvent event = new SeatStateEvent(showtimeId, seatId, status, expiresAt);
            String messageJson = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(seatEventsTopic.getTopic(), messageJson);
        } catch (Exception ex) {
            log.error("Failed to publish seat event for showtime {} seat {}: {}", showtimeId, seatId, ex.getMessage(), ex);
        }
    }

    public boolean publishAvailableIfUnheld(Long showtimeId, Long seatId) {
        try {
            SeatStateEvent event = new SeatStateEvent(showtimeId, seatId, "AVAILABLE", null);
            String messageJson = objectMapper.writeValueAsString(event);
            return redisLockService.publishAvailableIfUnheld(
                    showtimeId,
                    seatId,
                    seatEventsTopic.getTopic(),
                    messageJson
            );
        } catch (Exception ex) {
            log.error("Failed to atomic-publish AVAILABLE event for showtime {} seat {}: {}", showtimeId, seatId, ex.getMessage(), ex);
            return false;
        }
    }

    public boolean releaseHoldAndPublishAvailable(Long showtimeId, Long seatId, String expectedToken) {
        try {
            SeatStateEvent event = new SeatStateEvent(showtimeId, seatId, "AVAILABLE", null);
            String messageJson = objectMapper.writeValueAsString(event);
            return redisLockService.releaseHoldWithOwner(
                    showtimeId,
                    seatId,
                    expectedToken,
                    seatEventsTopic.getTopic(),
                    messageJson
            );
        } catch (Exception ex) {
            log.error("Failed to release hold and publish AVAILABLE event for showtime {} seat {}: {}",
                    showtimeId, seatId, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            SeatStateEvent event = objectMapper.readValue(json, SeatStateEvent.class);
            broadcastLocally(event);
        } catch (Exception ex) {
            log.error("Failed to process incoming Redis Pub/Sub message: {}", ex.getMessage(), ex);
        }
    }

    private void broadcastLocally(SeatStateEvent event) {
        CopyOnWriteArrayList<SseEmitter> emitters = showtimeEmitters.get(event.showtimeId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("seat-update")
                        .data(event));
            } catch (Exception ex) {
                removeEmitter(event.showtimeId(), emitter);
            }
        }
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : showtimeEmitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    public Set<Long> getActiveShowtimeIds() {
        return Collections.unmodifiableSet(showtimeEmitters.keySet());
    }

    private void removeEmitter(Long showtimeId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = showtimeEmitters.get(showtimeId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                showtimeEmitters.remove(showtimeId);
            }
        }
    }
}
