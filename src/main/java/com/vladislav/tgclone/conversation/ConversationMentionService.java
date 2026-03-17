package com.vladislav.tgclone.conversation;

import com.vladislav.tgclone.account.TelegramIdentity;
import com.vladislav.tgclone.account.TelegramIdentityRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationMentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{1,100})");

    private final ConversationMemberRepository conversationMemberRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;

    public ConversationMentionService(
        ConversationMemberRepository conversationMemberRepository,
        TelegramIdentityRepository telegramIdentityRepository
    ) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.telegramIdentityRepository = telegramIdentityRepository;
    }

    @Transactional(readOnly = true)
    public String normalizeTelegramMentions(Long conversationId, String body) {
        return rewriteMentions(body, buildTelegramToHermesMap(conversationId));
    }

    @Transactional(readOnly = true)
    public String resolveTelegramOutboundMentions(Long conversationId, String body) {
        return rewriteMentions(body, buildHermesToTelegramMap(conversationId));
    }

    private Map<String, String> buildTelegramToHermesMap(Long conversationId) {
        MentionDirectory mentionDirectory = buildMentionDirectory(conversationId);
        Map<String, String> replacements = new HashMap<>();
        for (Map.Entry<Long, String> entry : mentionDirectory.hermesByUserId().entrySet()) {
            String telegramUsername = mentionDirectory.telegramByUserId().get(entry.getKey());
            if (telegramUsername == null || telegramUsername.isBlank()) {
                continue;
            }
            replacements.put(telegramUsername.toLowerCase(Locale.ROOT), entry.getValue());
        }
        return replacements;
    }

    private Map<String, String> buildHermesToTelegramMap(Long conversationId) {
        MentionDirectory mentionDirectory = buildMentionDirectory(conversationId);
        Map<String, String> replacements = new HashMap<>();
        for (Map.Entry<Long, String> entry : mentionDirectory.hermesByUserId().entrySet()) {
            String telegramUsername = mentionDirectory.telegramByUserId().get(entry.getKey());
            if (telegramUsername == null || telegramUsername.isBlank()) {
                continue;
            }
            replacements.put(entry.getValue().toLowerCase(Locale.ROOT), telegramUsername);
        }
        return replacements;
    }

    private MentionDirectory buildMentionDirectory(Long conversationId) {
        if (conversationId == null) {
            return new MentionDirectory(Map.of(), Map.of());
        }

        List<ConversationMember> members = conversationMemberRepository.findAllByConversation_IdOrderByJoinedAtAsc(conversationId);
        if (members.isEmpty()) {
            return new MentionDirectory(Map.of(), Map.of());
        }

        Map<Long, String> hermesByUserId = new HashMap<>();
        List<Long> userIds = members.stream()
            .map(ConversationMember::getUserAccount)
            .map(user -> {
                hermesByUserId.put(user.getId(), user.getUsername());
                return user.getId();
            })
            .toList();

        Map<Long, String> telegramByUserId = telegramIdentityRepository.findAllByUserAccount_IdIn(userIds).stream()
            .filter(identity -> identity.getTelegramUsername() != null && !identity.getTelegramUsername().isBlank())
            .collect(
                HashMap::new,
                (map, identity) -> map.put(identity.getUserAccount().getId(), identity.getTelegramUsername()),
                HashMap::putAll
            );

        return new MentionDirectory(hermesByUserId, telegramByUserId);
    }

    private String rewriteMentions(String body, Map<String, String> replacements) {
        if (body == null || body.isBlank() || replacements.isEmpty()) {
            return body;
        }

        Matcher matcher = MENTION_PATTERN.matcher(body);
        StringBuffer rewritten = new StringBuffer();
        boolean changed = false;

        while (matcher.find()) {
            String username = matcher.group(1);
            String replacement = replacements.get(username.toLowerCase(Locale.ROOT));
            if (replacement == null || replacement.isBlank()) {
                continue;
            }

            changed = true;
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement("@" + replacement));
        }

        if (!changed) {
            return body;
        }

        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private record MentionDirectory(
        Map<Long, String> hermesByUserId,
        Map<Long, String> telegramByUserId
    ) {
    }
}
