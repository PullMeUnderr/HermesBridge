package com.vladislav.tgclone.config;

import com.vladislav.tgclone.account.ApiTokenService;
import com.vladislav.tgclone.conversation.ConversationService;
import com.vladislav.tgclone.security.AuthenticatedUser;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";
    private static final String USER_CONVERSATIONS_TOPIC_PREFIX = "/topic/users/";
    private static final String APP_CONVERSATION_READ_PREFIX = "/app/conversations/";

    private final ApiTokenService apiTokenService;
    private final ConversationService conversationService;

    public StompAuthenticationChannelInterceptor(
        ApiTokenService apiTokenService,
        ConversationService conversationService
    ) {
        this.apiTokenService = apiTokenService;
        this.conversationService = conversationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        return switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor, message);
            case SUBSCRIBE -> authorizeSubscription(accessor, message);
            case SEND -> authorizeSend(accessor, message);
            default -> message;
        };
    }

    private Message<?> authenticate(StompHeaderAccessor accessor, Message<?> message) {
        String token = extractBearerToken(accessor);
        if (token == null) {
            throw new AccessDeniedException("Missing bearer token");
        }

        AuthenticatedUser authenticatedUser = apiTokenService.authenticate(token)
            .orElseThrow(() -> new AccessDeniedException("Invalid bearer token"));

        accessor.setUser(new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of()));
        return message;
    }

    private Message<?> authorizeSubscription(StompHeaderAccessor accessor, Message<?> message) {
        AuthenticatedUser authenticatedUser = extractAuthenticatedUser(accessor.getUser());
        if (authenticatedUser == null) {
            throw new AccessDeniedException("Unauthorized subscription");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("Unsupported subscription destination");
        }

        if (destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
            Long conversationId = parseConversationId(destination);
            if (conversationId == null) {
                throw new AccessDeniedException("Invalid conversation destination");
            }

            conversationService.requireMembership(authenticatedUser, conversationId);
            return message;
        }

        if (destination.startsWith(USER_CONVERSATIONS_TOPIC_PREFIX)) {
            Long userId = parseUserConversationsUserId(destination);
            if (userId == null || !userId.equals(authenticatedUser.userId())) {
                throw new AccessDeniedException("Forbidden user destination");
            }
            return message;
        }

        throw new AccessDeniedException("Unsupported subscription destination");
    }

    private Message<?> authorizeSend(StompHeaderAccessor accessor, Message<?> message) {
        AuthenticatedUser authenticatedUser = extractAuthenticatedUser(accessor.getUser());
        if (authenticatedUser == null) {
            throw new AccessDeniedException("Unauthorized send destination");
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_CONVERSATION_READ_PREFIX)) {
            throw new AccessDeniedException("Unsupported send destination");
        }

        Long conversationId = parseAppConversationActionId(destination);
        if (conversationId == null) {
            throw new AccessDeniedException("Invalid send destination");
        }

        conversationService.requireMembership(authenticatedUser, conversationId);
        return message;
    }

    @Nullable
    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = firstNativeHeader(accessor, HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            authorization = firstNativeHeader(accessor, "authorization");
        }

        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        String normalized = authorization.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return normalized.substring("Bearer ".length()).trim();
        }

        return normalized;
    }

    @Nullable
    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }

        String value = values.getFirst();
        return value == null || value.isBlank() ? null : value;
    }

    @Nullable
    private AuthenticatedUser extractAuthenticatedUser(@Nullable Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
            && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }

        return null;
    }

    @Nullable
    private Long parseConversationId(String destination) {
        String rawId = destination.substring(CONVERSATION_TOPIC_PREFIX.length()).trim();
        if (rawId.isEmpty() || rawId.contains("/")) {
            return null;
        }

        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Long parseUserConversationsUserId(String destination) {
        String rawPath = destination.substring(USER_CONVERSATIONS_TOPIC_PREFIX.length()).trim();
        String[] segments = rawPath.split("/");
        if (segments.length != 2 || !"conversations".equals(segments[1])) {
            return null;
        }

        try {
            return Long.valueOf(segments[0]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Long parseAppConversationActionId(String destination) {
        String rawPath = destination.substring(APP_CONVERSATION_READ_PREFIX.length()).trim();
        String[] segments = rawPath.split("/");
        if (segments.length != 2) {
            return null;
        }

        String action = segments[1];
        if (!"read".equals(action) && !"typing".equals(action)) {
            return null;
        }

        try {
            return Long.valueOf(segments[0]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
