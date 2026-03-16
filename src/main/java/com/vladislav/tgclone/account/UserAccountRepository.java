package com.vladislav.tgclone.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsername(String username);

    Optional<UserAccount> findByIdAndActiveTrue(Long id);
}
