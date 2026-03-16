package com.vladislav.tgclone.account;

import com.vladislav.tgclone.common.NotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final TelegramIdentityRepository telegramIdentityRepository;

    public UserAccountService(
        UserAccountRepository userAccountRepository,
        TelegramIdentityRepository telegramIdentityRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.telegramIdentityRepository = telegramIdentityRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount requireActiveUser(Long userId) {
        return userAccountRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new NotFoundException("User %s not found".formatted(userId)));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByTelegramUserId(String telegramUserId) {
        if (telegramUserId == null || telegramUserId.isBlank()) {
            return Optional.empty();
        }
        return telegramIdentityRepository.findByTelegramUserId(telegramUserId)
            .map(TelegramIdentity::getUserAccount)
            .filter(UserAccount::isActive);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramIdentity> findTelegramIdentityByUserId(Long userId) {
        return telegramIdentityRepository.findByUserAccount_Id(userId);
    }
}
