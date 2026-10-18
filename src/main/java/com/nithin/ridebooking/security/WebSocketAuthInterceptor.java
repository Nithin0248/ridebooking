package com.nithin.ridebooking.security;

import com.nithin.ridebooking.service.RideService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern RIDE_TOPIC =
            Pattern.compile("^/topic/rides/(\\d{1,18})/(status|location)$");

    private final JwtService jwtService;
    private final RideService rideService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> requireUser(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing or invalid Authorization header");
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            String role = claims.get("role", String.class);
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessagingException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();

        if ("/user/queue/errors".equals(destination)) {
            return;
        }

        Matcher matcher = destination == null ? null : RIDE_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessagingException("Subscription not allowed: " + destination);
        }

        Long rideId = Long.parseLong(matcher.group(1));
        if (!rideService.isParticipant(user.getName(), rideId)) {
            throw new MessagingException("Not allowed to follow this ride");
        }
    }

    private Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessagingException("Not authenticated");
        }
        return user;
    }
}