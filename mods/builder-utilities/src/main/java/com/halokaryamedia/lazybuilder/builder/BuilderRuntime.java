package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.history.CompressedMemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.HistorySizingPolicy;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/** Shared Builder runtime ownership for history and bounded dispatch policy. */
public final class BuilderRuntime implements AutoCloseable {
    private static final long MIB = 1024L * 1024L;
    private final HistoryTimeline timeline;
    private final HistoryStorageRouter history;
    private final ExecutionBudget dispatchBudget;
    private final Path schematicDirectory;

    private BuilderRuntime(
            HistoryTimeline timeline,
            HistoryStorageRouter history,
            ExecutionBudget dispatchBudget,
            Path schematicDirectory
    ) {
        this.timeline = timeline;
        this.history = history;
        this.dispatchBudget = dispatchBudget;
        this.schematicDirectory = schematicDirectory;
    }

    public static BuilderRuntime createDefault() {
        Path builderDir = FabricLoader.getInstance().getConfigDir().resolve("lazybuilder");
        Path historyDir = builderDir.resolve("builder-history");
        Path schematicDir = builderDir.resolve("schematics");
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(8 * MIB, 64 * MIB),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new DiskChangeSetStorage(historyDir));
        return new BuilderRuntime(
                new HistoryTimeline(64),
                router,
                new ExecutionBudget(Duration.ofMillis(4), 4, 65_536, 16 * MIB),
                schematicDir
        );
    }

    public HistoryTimeline timeline() { return timeline; }
    public HistoryStorageRouter history() { return history; }
    public ExecutionBudget dispatchBudget() { return dispatchBudget; }
    public Path schematicDirectory() { return schematicDirectory; }

    @Override public void close() throws IOException { timeline.close(); }
}
