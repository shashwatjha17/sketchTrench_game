package com.sketchtrench.config;

import com.sketchtrench.guest.GuestChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS. Design:
 *  - Client connects to {@code /ws} (SockJS fallbacks for browsers).
 *  - Outbound topics: /topic/room/{id}, /topic/game/{id}, /topic/drawing/{id},
 *    /topic/chat/{id}, plus /queue and /user/... for private messages.
 *  - Client→server commands go to /app/... (the {@code @MessageMapping} surface).
 *
 * <p>{@code enableSimpleBroker} uses an IN-MEMORY broker — every player/room lives in
 * memory anyway, so a single instance is all there is.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final GuestChannelInterceptor authChannelInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /** All inbound frames (CONNECT, SUBSCRIBE, SEND) pass through guest auth first. */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
