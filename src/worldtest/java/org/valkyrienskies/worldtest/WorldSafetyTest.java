package org.valkyrienskies.worldtest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestServerContextImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Writes down everything in a world made without Valkyrien Skies, then opens that world with the mod and compares.
 */
public class WorldSafetyTest implements FabricClientGameTest {
    private static final int CHUNK_RADIUS = 3;
    private static final String SAVE_NAME = "world_safety";

    private static void log(final String message) {
        System.out.println("WORLDTEST " + message);
    }

    @Override
    public void runTest(final ClientGameTestContext context) {
        final Path store = Path.of(System.getenv("VS_WORLD_DIR"));
        final boolean modded = FabricLoader.getInstance().isModLoaded("valkyrienskies");
        log("valkyrienskies loaded=" + modded);
        try {
            if (modded || Files.exists(store.resolve("before.txt"))) {
                checkWorld(context, store, modded ? "modded" : "control");
            } else {
                makeWorld(context, store);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        log("finished");
    }

    private void makeWorld(final ClientGameTestContext context, final Path store) throws IOException {
        final Path saveDirectory;
        final List<String> before;
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            final TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("gamerule minecraft:spawn_mobs false");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 140 0");
            server.runOnServer(s -> {
                final ServerLevel level = s.overworld();
                for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
                    for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
                        level.setChunkForced(x, z, true);
                    }
                }
                s.getLevel(Level.NETHER).getChunk(0, 0);
                s.getLevel(Level.END).getChunk(0, 0);
            });
            server.runCommand("setblock 0 130 0 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}");
            server.runCommand("setblock 1 130 0 minecraft:furnace");
            server.runCommand("setblock 2 130 0 minecraft:redstone_lamp");
            server.runCommand("setblock 3 130 0 minecraft:hopper");
            server.runCommand("setblock 7 130 5 minecraft:stone");
            server.runCommand("summon minecraft:villager 5 131 5 {NoAI:1b,NoGravity:1b,CustomName:\"keep\"}");
            server.runCommand("summon minecraft:armor_stand 6 131 5 {NoGravity:1b}");
            server.runCommand("summon minecraft:item_frame 7 130 6 {Facing:3b,Item:{id:\"minecraft:diamond\",count:1}}");
            server.runCommand("summon minecraft:minecart 8 131 5 {NoGravity:1b}");
            server.runCommand("give @a minecraft:golden_apple 3");
            server.runCommand("scoreboard objectives add keep dummy");
            context.waitTicks(40);
            before = server.computeOnServer(WorldSafetyTest::snapshot);
            Files.write(store.resolve("blocks-before.txt"), server.computeOnServer(WorldSafetyTest::blocks));
            saveDirectory = world.getWorldSave().getSaveDirectory();
        }
        copyFolder(saveDirectory, store.resolve("world"));
        Files.write(store.resolve("before.txt"), before);
        log("made a world with " + before.size() + " lines of facts");
        log("files " + fileList(store.resolve("world")));
    }

    private void checkWorld(final ClientGameTestContext context, final Path store, final String tag) throws IOException {
        final List<String> before = Files.readAllLines(store.resolve("before.txt"));
        final List<String> blocksBefore = Files.readAllLines(store.resolve("blocks-before.txt"));
        final Path save = context.computeOnClient(mc -> mc.gameDirectory.toPath()).resolve("saves").resolve(SAVE_NAME);
        copyFolder(store.resolve("world"), save);

        for (int run = 1; run <= 2; run++) {
            context.runOnClient(mc -> mc.createWorldOpenFlows().openWorld(SAVE_NAME, () -> log("opening was cancelled")));
            context.waitFor(mc -> mc.getSingleplayerServer() != null && mc.level != null && mc.player != null, 3600);
            context.waitTicks(120);
            final MinecraftServer running = context.computeOnClient(Minecraft::getSingleplayerServer);
            final List<String> after = new TestServerContextImpl(running).computeOnServer(WorldSafetyTest::snapshot);
            Files.write(store.resolve("after-" + tag + run + ".txt"), after);
            final List<String> blocksNow = new TestServerContextImpl(running).computeOnServer(WorldSafetyTest::blocks);
            final java.util.Set<String> was = new java.util.HashSet<>(blocksBefore);
            final java.util.Set<String> now = new java.util.HashSet<>(blocksNow);
            final List<String> blocksChanged = blocksBefore.stream().filter(line -> !now.contains(line)).toList();
            final List<String> blocksAdded = blocksNow.stream().filter(line -> !was.contains(line)).toList();
            log("run " + run + " blocks compared=" + blocksBefore.size() + " changed=" + blocksChanged.size() + " new=" + blocksAdded.size());
            final Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (final String line : blocksChanged) {
                kinds.merge("from " + line.substring(line.lastIndexOf(' ') + 1), 1, Integer::sum);
            }
            for (final String line : blocksAdded) {
                kinds.merge("to " + line.substring(line.lastIndexOf(' ') + 1), 1, Integer::sum);
            }
            log("  block changes by kind " + kinds);
            blocksChanged.stream().limit(6).forEach(line -> log("  was " + line));
            blocksAdded.stream().limit(6).forEach(line -> log("  now " + line));
            final List<String> changed = new ArrayList<>(before);
            changed.removeAll(after);
            final List<String> added = new ArrayList<>(after);
            added.removeAll(before);
            log("run " + run + " facts=" + after.size() + " changed=" + changed.size() + " added=" + added.size() +
                " identical=" + before.equals(after));
            changed.stream().filter(line -> !line.startsWith("chunk")).limit(10).forEach(line -> log("  was " + line));
            added.stream().filter(line -> !line.startsWith("chunk")).limit(10).forEach(line -> log("  now " + line));

            context.runOnClient(mc -> mc.disconnect(new TitleScreen(), false));
            context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, 3600);
            context.waitTicks(20);
        }
        log("files " + fileList(save));
    }

    /** Every fact worth keeping, one per line, in an order that doesn't depend on anything but the content. */
    private static List<String> snapshot(final MinecraftServer server) {
        final List<String> facts = new ArrayList<>();
        facts.add("seed " + server.getWorldGenSettings().options().seed());
        facts.add("dimensions " + server.levelKeys().stream().map(ResourceKey::identifier).map(Object::toString).sorted().toList());
        facts.add("scoreboard " + server.getScoreboard().getObjectiveNames());
        for (final ResourceKey<Level> dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            final ServerLevel level = server.getLevel(dimension);
            final int radius = dimension == Level.OVERWORLD ? CHUNK_RADIUS : 0;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final LevelChunk chunk = level.getChunk(x, z);
                    facts.add("chunk " + dimension.identifier() + " " + x + " " + z + " " + digest(level, chunk));
                    final List<String> blockEntities = new ArrayList<>();
                    for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        blockEntities.add("block entity " + blockEntity.getBlockPos().toShortString() + " " +
                            blockEntity.saveWithoutMetadata(server.registryAccess()));
                    }
                    Collections.sort(blockEntities);
                    facts.addAll(blockEntities);
                }
            }
        }
        final List<String> entities = new ArrayList<>();
        for (final Entity entity : server.overworld().getAllEntities()) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                entities.add(String.format("entity %s %s %.3f %.3f %.3f", entity.getUUID(), entity.getType(), entity.getX(),
                    entity.getY(), entity.getZ()));
            }
        }
        Collections.sort(entities);
        facts.addAll(entities);
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            final Inventory inventory = server.getPlayerList().getPlayers().get(0).getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                final ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    facts.add("inventory " + slot + " " + stack);
                }
            }
        }
        return facts;
    }

    /** Every block that isn't plain terrain below the surface, each on its own line. */
    private static List<String> blocks(final MinecraftServer server) {
        final List<String> lines = new ArrayList<>();
        final ServerLevel level = server.overworld();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                final LevelChunk chunk = level.getChunk(cx, cz);
                final int startX = chunk.getPos().getMinBlockX();
                final int startZ = chunk.getPos().getMinBlockZ();
                for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            final BlockState state = chunk.getBlockState(pos.set(startX + x, y, startZ + z));
                            if (!state.isAir() && !state.is(net.minecraft.world.level.block.Blocks.STONE)
                                && !state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) {
                                lines.add((startX + x) + " " + y + " " + (startZ + z) + " " + state);
                            }
                        }
                    }
                }
            }
        }
        return lines;
    }

    private static String digest(final Level level, final LevelChunk chunk) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final Map<BlockState, byte[]> names = new IdentityHashMap<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int startX = chunk.getPos().getMinBlockX();
        final int startZ = chunk.getPos().getMinBlockZ();
        for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    digest.update(names.computeIfAbsent(chunk.getBlockState(pos.set(startX + x, y, startZ + z)),
                        state -> state.toString().getBytes()));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fileList(final Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).filter(file -> !file.getFileName().toString().equals("session.lock"))
                .map(file -> folder.relativize(file).toString()).sorted().toList().toString();
        }
    }

    private static void copyFolder(final Path from, final Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (final Path file : files.toList()) {
                final Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else if (!file.getFileName().toString().equals("session.lock")) {
                    Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
