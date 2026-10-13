package com.ticketbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String SEAT_EVENTS_TOPIC = "showtime:seat-events";

    @Bean
    public ChannelTopic seatEventsTopic() {
        return new ChannelTopic(SEAT_EVENTS_TOPIC);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setMaxSubscriptionRegistrationWaitingTime(10000L);
        return container;
    }
}
