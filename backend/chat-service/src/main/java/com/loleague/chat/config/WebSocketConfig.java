package com.loleague.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.security.Principal;

/**
 * STOMP over native WebSocket.
 *  - CONNECT must carry a valid "ticket" header (see WsTicketService)
 *  - a student may only SUBSCRIBE to their own college's topics
 *  - SEND is only allowed to /app/chat.send
 *
 * Uses the in-memory simple broker: perfect for one chat instance (thousands of users).
 * To run several instances, switch to enableStompBrokerRelay(...) backed by RabbitMQ.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatProperties props;
    private final WsTicketService tickets;

    public WebSocketConfig(ChatProperties props, WsTicketService tickets) {
        this.props = props;
        this.tickets = tickets;
    }

    @Bean
    public ThreadPoolTaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(props.chat().origins());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{20_000, 20_000})
                .setTaskScheduler(brokerHeartbeatScheduler());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(15_000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                StompCommand command = accessor.getCommand();
                if (command == StompCommand.CONNECT) {
                    String ticket = accessor.getFirstNativeHeader("ticket");
                    if (ticket == null) {
                        throw new MessageDeliveryException("Missing ticket");
                    }
                    try {
                        accessor.setUser(tickets.verify(ticket));
                    } catch (RuntimeException e) {
                        throw new MessageDeliveryException("Invalid or expired ticket");
                    }
                    return message;
                }

                Principal user = accessor.getUser();
                if (!(user instanceof ChatPrincipal me)) {
                    if (command == StompCommand.DISCONNECT) {
                        return message;
                    }
                    throw new MessageDeliveryException("Not authenticated");
                }
                String dest = accessor.getDestination();
                if (command == StompCommand.SUBSCRIBE) {
                    boolean allowed = me.roomTopic().equals(dest)
                            || me.presenceTopic().equals(dest)
                            || "/user/queue/errors".equals(dest);
                    if (!allowed) {
                        throw new MessageDeliveryException("You can only join your own college's room");
                    }
                }
                if (command == StompCommand.SEND && !"/app/chat.send".equals(dest)) {
                    throw new MessageDeliveryException("Unknown destination");
                }
                return message;
            }
        });
    }
}
