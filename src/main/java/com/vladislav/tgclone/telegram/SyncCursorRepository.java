package com.vladislav.tgclone.telegram;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncCursorRepository extends JpaRepository<SyncCursor, String> {
}
