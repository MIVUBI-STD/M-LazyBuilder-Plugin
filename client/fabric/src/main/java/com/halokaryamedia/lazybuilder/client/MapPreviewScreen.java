package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.world.Heightmap;
import net.minecraft.text.Text;

/** Native LazyBuilder map preview; Xaero is not required at runtime. */
public final class MapPreviewScreen extends Screen {
    private static final int RADIUS = 24;
    private static final int TILE = 8;
    private final ClientMapController maps;
    private Integer firstX;
    private Integer firstZ;
    private boolean exportMode;
    private int centerX;
    private int centerZ;
    private int zoom = 2;

    public MapPreviewScreen(ClientMapController maps) {
        super(Text.literal("LazyBuilder Map Preview"));
        this.maps = maps;
    }

    @Override
    protected void init() {
        int left = width / 2 - (RADIUS * TILE) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Teleport Here"), button -> {
            exportMode = false;
            firstX = null;
            firstZ = null;
        }).dimensions(left, height - 48, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export Area"), button -> {
            exportMode = true;
            firstX = null;
            firstZ = null;
        }).dimensions(left + 116, height - 48, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(left + 222, height - 48, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+") , button -> zoom = Math.max(1, zoom - 1))
                .dimensions(width - 92, 18, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-") , button -> zoom = Math.min(8, zoom + 1))
                .dimensions(width - 68, 18, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Center"), button -> centerOnPlayer())
                .dimensions(width - 132, 18, 56, 20).build());
        centerOnPlayer();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int mapSize = RADIUS * TILE;
        int left = width / 2 - mapSize / 2;
        int top = height / 2 - mapSize / 2;
        context.fill(left - 2, top - 2, left + mapSize + 2, top + mapSize + 2, 0xFF20252B);

        ClientWorld world = client == null ? null : client.world;
        if (world != null && client.player != null) {
            for (int z = -RADIUS / 2; z < RADIUS / 2; z++) {
                for (int x = -RADIUS / 2; x < RADIUS / 2; x++) {
                    int blockX = centerX + x * zoom;
                    int blockZ = centerZ + z * zoom;
                    int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, blockX, blockZ);
                    int color = terrainColor(world, blockX, y - 1, blockZ, y);
                    context.fill(left + (x + RADIUS / 2) * TILE, top + (z + RADIUS / 2) * TILE,
                            left + (x + RADIUS / 2 + 1) * TILE, top + (z + RADIUS / 2 + 1) * TILE, color);
                }
            }
            context.fill(width / 2 - 3, height / 2 - 3, width / 2 + 4, height / 2 + 4, 0xFFFF3333);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Center: " + centerX + ", " + centerZ + "  Zoom 1:" + zoom),
                    width / 2, top - 18, 0xFFFFFF);
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(exportMode ? (firstX == null ? "Select first export corner" : "Select second export corner")
                        : "Click the map to teleport"), width / 2, top + mapSize + 10, 0xCCCCCC);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || client == null || client.player == null) return super.mouseClicked(mouseX, mouseY, button);
        int mapSize = RADIUS * TILE;
        int left = width / 2 - mapSize / 2;
        int top = height / 2 - mapSize / 2;
        if (mouseX < left || mouseX >= left + mapSize || mouseY < top || mouseY >= top + mapSize) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = client.player.getBlockX() + (int) ((mouseX - width / 2) / TILE) * 2;
        int z = client.player.getBlockZ() + (int) ((mouseY - height / 2) / TILE) * 2;
        x = centerX + (int) ((mouseX - width / 2) / TILE) * zoom;
        z = centerZ + (int) ((mouseY - height / 2) / TILE) * zoom;
        if (!exportMode) {
            maps.teleportCurrent(x, z);
            close();
        } else if (firstX == null) {
            firstX = x;
            firstZ = z;
        } else {
            maps.exportAreaCurrent(firstX, firstZ, x, z, "JAVA_1_21_4", "area-" + System.currentTimeMillis());
            close();
        }
        return true;
    }

    private void centerOnPlayer() {
        if (client != null && client.player != null) {
            centerX = client.player.getBlockX();
            centerZ = client.player.getBlockZ();
        }
    }

    private static int terrainColor(ClientWorld world, int x, int y, int z, int height) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.getBlockState(pos).isOf(Blocks.WATER) || world.getBlockState(pos).isOf(Blocks.ICE)) return 0xFF3B82C4;
        if (world.getBlockState(pos).isOf(Blocks.SAND) || world.getBlockState(pos).isOf(Blocks.SANDSTONE)) return 0xFFD9C27A;
        if (world.getBlockState(pos).isOf(Blocks.SNOW) || world.getBlockState(pos).isOf(Blocks.SNOW_BLOCK)) return 0xFFE8F0F5;
        if (world.getBlockState(pos).isOf(Blocks.STONE) || world.getBlockState(pos).isOf(Blocks.DEEPSLATE)) return 0xFF777C82;
        int shade = Math.max(40, Math.min(220, 70 + height));
        return 0xFF000000 | (shade / 3 << 16) | (shade << 8) | (shade / 4);
    }
}
