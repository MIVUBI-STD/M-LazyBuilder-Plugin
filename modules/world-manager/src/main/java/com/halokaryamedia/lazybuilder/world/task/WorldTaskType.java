package com.halokaryamedia.lazybuilder.world.task;

/** Long-running desktop-visible operations that must not block the control bridge. */
public enum WorldTaskType {
    CLONE,
    BACKUP,
    IMPORT,
    EXPORT,
    ARCHIVE,
    RESTORE,
    DELETE
}
