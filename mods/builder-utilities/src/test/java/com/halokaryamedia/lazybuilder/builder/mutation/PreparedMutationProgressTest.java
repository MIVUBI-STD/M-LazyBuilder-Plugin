package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparedMutationProgressTest {
    @Test
    void processedWorkAdvancesMonotonicallyWhileRunning() throws Exception {
        StoredChangeSet stored;
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("progress")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{
                            LocalBlockPosition.pack(0, 64, 0),
                            LocalBlockPosition.pack(1, 64, 0)
                    },
                    new int[]{0, 0},
                    new int[]{1, 1}
            ));
            stored = writer.commit();
        }

        try (HistoryTimeline timeline = new HistoryTimeline(4);
             PreparedMutationSession session = new PreparedMutationSession(
                     new PreparedMaterialMutation(stored, 2), timeline)) {
            session.startDispatch();
            session.noteProcessedWork(1);
            assertEquals(1, session.lifecycle().processedWork());
            session.noteProcessedWork(2);
            assertEquals(1.0, session.lifecycle().progressFraction(), 1.0e-9);
        }
    }
}
