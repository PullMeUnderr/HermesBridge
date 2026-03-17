package com.vladislav.tgclone.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramIdentityRepository extends JpaRepository<TelegramIdentity, Long> {

    Optional<TelegramIdentity> findByTelegramUserId(String telegramUserId);

    Optional<TelegramIdentity> findByUserAccount_Id(Long userAccountId);

    List<TelegramIdentity> findAllByUserAccount_IdIn(Collection<Long> userAccountIds);
}
