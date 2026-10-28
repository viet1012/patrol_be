package com.example.patrol_be.permission;

import java.util.Locale;

public enum PatrolAction {
    BEFORE,
    AFTER,
    RECHECK,
    SUMMARY;

    /**
     * Key trong JSON /api/hr/me (FE đọc theo PatrolAction.name: before/after/recheck/summary).
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
