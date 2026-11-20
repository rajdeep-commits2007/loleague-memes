package com.loleague.chat.config;

import java.security.Principal;
import java.util.UUID;

/** The student attached to a WebSocket session after a valid CONNECT ticket. */
public record ChatPrincipal(UUID id, String username, String collegeId) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public String roomTopic() {
        return "/topic/college." + collegeId;
    }

    public String presenceTopic() {
        return roomTopic() + ".presence";
    }
}
