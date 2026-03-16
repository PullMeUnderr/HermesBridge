package com.vladislav.tgclone.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sync_cursors")
public class SyncCursor {

    @Id
    @Column(name = "cursor_key", nullable = false, length = 100)
    private String cursorKey;

    @Column(name = "next_offset", nullable = false)
    private long nextOffset;

    protected SyncCursor() {
    }

    public SyncCursor(String cursorKey, long nextOffset) {
        this.cursorKey = cursorKey;
        this.nextOffset = nextOffset;
    }

    public String getCursorKey() {
        return cursorKey;
    }

    public long getNextOffset() {
        return nextOffset;
    }

    public void setNextOffset(long nextOffset) {
        this.nextOffset = nextOffset;
    }
}
