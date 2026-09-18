package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;

public interface ChangeSetStorage {
    HistoryStorageTier tier();

    ChangeSetWriter begin(String operationId) throws IOException;
}
