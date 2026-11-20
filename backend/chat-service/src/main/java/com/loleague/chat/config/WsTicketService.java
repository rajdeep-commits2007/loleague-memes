package com.loleague.chat.config;

import com.loleague.common.security.CurrentUser;
import com.loleague.common.security.JwtSupport;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Browsers can't attach our HttpOnly cookie to a cross-origin WebSocket, so the client first calls
 * GET /api/chat/ws-ticket (cookie-authenticated through the Next.js proxy) and receives a 60-second
 * "ws" token it passes in the STOMP CONNECT frame. The ticket can't be used as an HTTP access token.
 */
@Component
public class WsTicketService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final ChatProperties props;

    public WsTicketService(ChatProperties props) {
        this.props = props;
        this.encoder = JwtSupport.encoder(props.jwt().secret());
        this.decoder = JwtSupport.decoder(props.jwt().secret(), props.jwt().issuer(), JwtSupport.TYPE_WS);
    }

    public String issue(CurrentUser me) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(me.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.chat().ticketTtl()))
                .claim(JwtSupport.CLAIM_USERNAME, me.username())
                .claim(JwtSupport.CLAIM_COLLEGE, me.collegeId())
                .claim(JwtSupport.CLAIM_TYPE, JwtSupport.TYPE_WS)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Throws JwtException when the ticket is invalid or expired. */
    public ChatPrincipal verify(String ticket) {
        Jwt jwt = decoder.decode(ticket);
        return new ChatPrincipal(UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(JwtSupport.CLAIM_USERNAME),
                jwt.getClaimAsString(JwtSupport.CLAIM_COLLEGE));
    }
}
