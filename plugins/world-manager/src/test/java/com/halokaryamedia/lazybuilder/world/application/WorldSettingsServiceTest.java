package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSettingsServiceTest {
    private static final WorldSpawningSettings DEFAULT_SPAWNING = new WorldSpawningSettings(
            false, false, false, false, false, false, false, false, false, false
    );

    @Test
    void snapshotCombinesDurablePreferencesWithRuntimeState() {
        Fixture fixture = fixture();
        fixture.runtime().runtimeSettings = new WorldRuntimeSettings(
                WorldDifficulty.NORMAL,
                false,
                WorldWeather.CLEAR,
                6000,
                new WorldSpawnSetting(0, 65, 0, 0, 0),
                DEFAULT_SPAWNING,
                List.of(new GameRuleSetting("doDaylightCycle", GameRuleValueType.BOOLEAN, "false"))
        );

        WorldSettingsSnapshot snapshot = fixture.service().snapshot(fixture.world().id());

        assertEquals(WorldGameMode.CREATIVE, snapshot.defaultGameMode());
        assertFalse(snapshot.runtime().pvpEnabled());
        assertEquals("doDaylightCycle", snapshot.runtime().gamerules().getFirst().name());
    }

    @Test
    void durableGameModePreferencePersistsThroughRegistryOwner() {
        Fixture fixture = fixture();

        WorldRecord updated = fixture.service().setDefaultGameMode(fixture.world().id(), WorldGameMode.ADVENTURE);

        assertEquals("ADVENTURE", updated.defaultGameMode());
        assertEquals(updated, fixture.persistence().saved.getFirst());
    }

    @Test
    void failedPreferencePersistenceRestoresPreviousRegistryState() {
        Fixture fixture = fixture();
        fixture.persistence().failNextSave = true;

        assertThrows(IllegalStateException.class,
                () -> fixture.service().setDefaultGameMode(fixture.world().id(), WorldGameMode.SPECTATOR));

        WorldRecord current = fixture.registry().find(fixture.world().id()).orElseThrow();
        assertEquals(WorldRecord.DEFAULT_GAME_MODE, current.defaultGameMode());
    }

    @Test
    void settingsMutationIsRejectedWhileHeavyOperationOwnsWorld() {
        Fixture fixture = fixture();
        try (WorldOperationCoordinator.Lease ignored =
                     fixture.operations().acquire(fixture.world().id(), WorldOperationType.EXPORT)) {
            assertThrows(IllegalStateException.class,
                    () -> fixture.service().setDifficulty(
                            fixture.world().id(),
                            WorldDifficulty.HARD));
        }
        assertEquals(null, fixture.runtime().lastDifficulty);
    }

    @Test
    void settingsLeaseIsReleasedAfterMutation() {
        Fixture fixture = fixture();
        fixture.service().setDifficulty(fixture.world().id(), WorldDifficulty.HARD);

        assertFalse(fixture.operations().isBusy(fixture.world().id()));
        try (WorldOperationCoordinator.Lease ignored =
                     fixture.operations().acquire(fixture.world().id(), WorldOperationType.DELETE)) {
            assertTrue(fixture.operations().isBusy(fixture.world().id()));
        }
    }

    @Test
    void batchSettingsKeepsOneExclusiveLeaseAcrossAllMutations() {
        Fixture fixture = fixture();
        fixture.runtime().assertBusyCoordinator = fixture.operations();
        fixture.runtime().assertBusyWorld = fixture.world().id();

        WorldSettingsSnapshot snapshot = fixture.service().applyBatch(
                fixture.world().id(),
                WorldGameMode.ADVENTURE,
                12_000L,
                WorldWeather.RAIN,
                false,
                true,
                false
        );

        assertEquals(WorldGameMode.ADVENTURE, snapshot.defaultGameMode());
        assertEquals(5, fixture.runtime().busyAssertions,
                "every runtime mutation in the PATCH must observe the same active SETTINGS lease");
        assertFalse(fixture.operations().isBusy(fixture.world().id()));
    }

    @Test
    void batchFailureRollsBackEarlierRuntimeAndMetadataChanges() {
        Fixture fixture = fixture();
        fixture.runtime().runtimeSettings = new WorldRuntimeSettings(
                WorldDifficulty.NORMAL,
                false,
                WorldWeather.CLEAR,
                6_000,
                new WorldSpawnSetting(0, 65, 0, 0, 0),
                DEFAULT_SPAWNING,
                List.of(
                        new GameRuleSetting(
                                "doDaylightCycle",
                                GameRuleValueType.BOOLEAN,
                                "false"),
                        new GameRuleSetting(
                                "doWeatherCycle",
                                GameRuleValueType.BOOLEAN,
                                "true")
                )
        );
        fixture.runtime().failGameRuleName = "doWeatherCycle";

        assertThrows(IllegalStateException.class, () -> fixture.service().applyBatch(
                fixture.world().id(),
                WorldGameMode.ADVENTURE,
                12_000L,
                WorldWeather.RAIN,
                true,
                true,
                false
        ));

        WorldRecord persisted = fixture.registry().find(fixture.world().id()).orElseThrow();
        assertEquals(WorldRecord.DEFAULT_GAME_MODE, persisted.defaultGameMode());
        assertEquals(6_000L, fixture.runtime().lastTime);
        assertEquals(WorldWeather.CLEAR, fixture.runtime().lastWeather);
        assertEquals(DEFAULT_SPAWNING.naturalSpawning(), fixture.runtime().lastSpawnEnabled);
        assertTrue(fixture.runtime().restoredRules.contains("doDaylightCycle=false"));
        assertEquals(WorldRecord.DEFAULT_GAME_MODE,
                fixture.persistence().saved.getFirst().defaultGameMode());
        assertFalse(fixture.operations().isBusy(fixture.world().id()));
    }

    @Test
    void runtimeMutationsDelegateWithoutCreatingParallelState() {
        Fixture fixture = fixture();
        UUID playerId = UUID.randomUUID();

        fixture.service().setDifficulty(fixture.world().id(), WorldDifficulty.HARD);
        fixture.service().setPvp(fixture.world().id(), true);
        fixture.service().setTime(fixture.world().id(), 12000);
        fixture.service().setWeather(fixture.world().id(), WorldWeather.RAIN);
        fixture.service().setGameRule(fixture.world().id(), "doDaylightCycle", "true");
        fixture.service().setSpawnToPlayer(playerId, fixture.world().id());

        assertEquals(WorldDifficulty.HARD, fixture.runtime().lastDifficulty);
        assertTrue(fixture.runtime().lastPvp);
        assertEquals(12000, fixture.runtime().lastTime);
        assertEquals(WorldWeather.RAIN, fixture.runtime().lastWeather);
        assertEquals("doDaylightCycle=true", fixture.runtime().lastRule);
        assertEquals(playerId, fixture.runtime().lastSpawnPlayer);
    }

    @Test
    void spawningControlsDelegateAndReturnCanonicalRuntimeSnapshot() {
        Fixture fixture = fixture();
        fixture.runtime().runtimeSettings = runtimeSettings(DEFAULT_SPAWNING);

        WorldSettingsSnapshot snapshot = fixture.service().setSpawning(
                fixture.world().id(),
                WorldSpawnControl.WATER,
                false
        );

        assertEquals(WorldSpawnControl.WATER, fixture.runtime().lastSpawnControl);
        assertFalse(fixture.runtime().lastSpawnEnabled);
        assertEquals(DEFAULT_SPAWNING, snapshot.runtime().spawning());
    }

    @Test
    void resetToBuildReadyIsExplicitAndRestoresEntryGameModePreference() {
        Fixture fixture = fixture();
        fixture.service().setDefaultGameMode(fixture.world().id(), WorldGameMode.SURVIVAL);

        WorldSettingsSnapshot reset = fixture.service().resetToBuildReady(fixture.world().id());

        assertTrue(fixture.runtime().buildReadyApplied);
        assertEquals(WorldGameMode.CREATIVE, reset.defaultGameMode());
        assertEquals("CREATIVE", fixture.registry().find(fixture.world().id()).orElseThrow().defaultGameMode());
    }

    @Test
    void failedBuildReadyMetadataSaveLeavesRuntimeUntouched() {
        Fixture fixture = fixture();
        fixture.service().setDefaultGameMode(fixture.world().id(), WorldGameMode.SURVIVAL);
        fixture.runtime().buildReadyApplied = false;
        fixture.persistence().failNextSave = true;

        assertThrows(IllegalStateException.class,
                () -> fixture.service().resetToBuildReady(fixture.world().id()));

        assertFalse(fixture.runtime().buildReadyApplied);
        assertEquals("SURVIVAL", fixture.registry().find(fixture.world().id()).orElseThrow().defaultGameMode());
    }

    @Test
    void runtimeFailureRollsBackBuildReadyMetadataPreference() {
        Fixture fixture = fixture();
        fixture.service().setDefaultGameMode(fixture.world().id(), WorldGameMode.SURVIVAL);
        fixture.runtime().failBuildReady = true;

        assertThrows(IllegalStateException.class,
                () -> fixture.service().resetToBuildReady(fixture.world().id()));

        assertEquals("SURVIVAL", fixture.registry().find(fixture.world().id()).orElseThrow().defaultGameMode());
        assertEquals("SURVIVAL", fixture.persistence().saved.getFirst().defaultGameMode());
    }

    private static WorldRuntimeSettings runtimeSettings(WorldSpawningSettings spawning) {
        return new WorldRuntimeSettings(
                WorldDifficulty.NORMAL,
                false,
                WorldWeather.CLEAR,
                6000,
                new WorldSpawnSetting(0, 65, 0, 0, 0),
                spawning,
                List.of()
        );
    }

    private static Fixture fixture() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(),
                "Build",
                "Build",
                WorldKind.FLAT,
                WorldLifecycle.ACTIVE
        );
        registry.register(world);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        FakeRuntime runtime = new FakeRuntime();
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        WorldSettingsService service = new WorldSettingsService(
                registry,
                persistence,
                runtimeService,
                runtime,
                operations,
                BuildReadyPolicy.defaults()
        );
        return new Fixture(registry, persistence, runtime, operations, service, world);
    }

    private record Fixture(
            WorldRegistry registry,
            MemoryPersistence persistence,
            FakeRuntime runtime,
            WorldOperationCoordinator operations,
            WorldSettingsService service,
            WorldRecord world
    ) { }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();
        private boolean failNextSave;

        @Override public List<WorldRecord> load() { return saved; }

        @Override
        public void save(List<WorldRecord> worlds) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("test failure");
            }
            saved = List.copyOf(worlds);
        }
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private WorldRuntimeSettings runtimeSettings = runtimeSettings(DEFAULT_SPAWNING);
        private WorldDifficulty lastDifficulty;
        private boolean lastPvp;
        private long lastTime;
        private WorldWeather lastWeather;
        private String lastRule;
        private UUID lastSpawnPlayer;
        private WorldSpawnControl lastSpawnControl;
        private boolean lastSpawnEnabled;
        private boolean buildReadyApplied;
        private boolean failBuildReady;
        private WorldOperationCoordinator assertBusyCoordinator;
        private WorldId assertBusyWorld;
        private int busyAssertions;
        private String failGameRuleName;
        private final java.util.List<String> restoredRules = new java.util.ArrayList<>();

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return true; }
        @Override public void loadWorld(WorldRecord world) { }
        @Override public void unloadWorld(WorldRecord world) { }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
        @Override public WorldRuntimeSettings readSettings(WorldRecord world) { return runtimeSettings; }
        @Override public void setDifficulty(WorldRecord world, WorldDifficulty difficulty) { lastDifficulty = difficulty; }
        @Override public void setPvp(WorldRecord world, boolean enabled) { lastPvp = enabled; }
        @Override public void setTime(WorldRecord world, long ticks) { assertBusy(); lastTime = ticks; }
        @Override public void setWeather(WorldRecord world, WorldWeather weather) { assertBusy(); lastWeather = weather; }
        @Override
        public void setGameRule(WorldRecord world, String ruleName, String value) {
            assertBusy();
            if (ruleName.equals(failGameRuleName)) {
                failGameRuleName = null;
                throw new IllegalStateException("runtime gamerule failure");
            }
            lastRule = ruleName + "=" + value;
            restoredRules.add(lastRule);
        }
        @Override public void setSpawnToPlayer(UUID playerId, WorldRecord world) { lastSpawnPlayer = playerId; }
        @Override public void setSpawning(WorldRecord world, WorldSpawnControl control, boolean enabled) {
            assertBusy();
            lastSpawnControl = control;
            lastSpawnEnabled = enabled;
        }

        private void assertBusy() {
            if (assertBusyCoordinator == null) return;
            assertTrue(assertBusyCoordinator.isBusy(assertBusyWorld));
            assertEquals(WorldOperationType.SETTINGS,
                    assertBusyCoordinator.activeOperation(assertBusyWorld));
            busyAssertions++;
        }

        @Override
        public void applyBuildReady(WorldRecord world, BuildReadyPolicy policy) {
            if (failBuildReady) throw new IllegalStateException("runtime reset failure");
            buildReadyApplied = true;
            runtimeSettings = new WorldRuntimeSettings(
                    policy.difficulty(),
                    policy.pvpEnabled(),
                    policy.weather(),
                    policy.timeOfDayTicks(),
                    runtimeSettings.spawn(),
                    runtimeSettings.spawning(),
                    runtimeSettings.gamerules()
            );
        }
    }
}
