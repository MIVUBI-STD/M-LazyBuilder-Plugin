package com.halokaryamedia.lazybuilder.world.application;

/** Exclusive request-bound operation types for one managed world. */
public enum WorldOperationType {
    CLONE,
    BACKUP,
    ARCHIVE,
    RESTORE,
    DELETE,
    IMPORT,
    EXPORT,
    CONVERSION
}
