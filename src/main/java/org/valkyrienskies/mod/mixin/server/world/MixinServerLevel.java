package org.valkyrienskies.mod.mixin.server.world;

import org.valkyrienskies.mod.common.config.AddedFeatures;
import org.valkyrienskies.mod.common.world.ShipTerrainTickets;
import org.valkyrienskies.core.api.ships.ServerShip;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Wing;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.core.api.util.AerodynamicUtils;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.WingBlock;
import org.valkyrienskies.mod.common.config.DimensionParametersResolver;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.common.util.VSServerLevel;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.DistanceManagerAccessor;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;
import org.valkyrienskies.mod.mixinducks.world.level.NoVSLevelDuck;
import org.valkyrienskies.mod.util.McMathUtilKt;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IShipObjectWorldServerProvider, VSServerLevel {
    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    @Shadow
    @NotNull
    public abstract MinecraftServer getServer();

    @Shadow
    public abstract int sectionsToVillage(SectionPos arg);


    // Map from ChunkPos to the list of voxel chunks that chunk owns
    @Unique
    private final Map<ChunkPos, List<Vector3ic>> vs$knownChunks = new HashMap<>();

    // Maps chunk pos to number of ticks we have considered unloading the chunk
    @Unique
    private final Long2LongOpenHashMap vs$chunksToUnload = new Long2LongOpenHashMap();

    // How many ticks we wait before unloading a chunk
    @Unique
    private static final long VS$CHUNK_UNLOAD_THRESHOLD = 100;

    @Nullable
    @Override
    public VsiServerShipWorld getShipObjectWorld() {
        return ((IShipObjectWorldServerProvider) getServer()).getShipObjectWorld();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInit(final CallbackInfo ci) {
        // Fake / wrapped ServerLevels are used by Create, Supplementaries and maybe others.
        if (this instanceof NoVSLevelDuck) return;

        // This only happens when overworld gets loaded on startup, we have a mixin in MixinMinecraftServer for this specific case
        if (getShipObjectWorld() != null) {
            DimensionParametersResolver.Parameters params = DimensionParametersResolver.INSTANCE.getDimensionMap().get(
                VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this)
            );
            if (params != null) {
                getShipObjectWorld().addDimension(
                    VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this),
                    VSGameUtilsKt.getYRange((ServerLevel) (Object) this),
                    params.getGravity(),
                    params.getSeaLevel(),
                    params.getMaxY()
                );
                return;
            }
            getShipObjectWorld().addDimension(
                VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this),
                VSGameUtilsKt.getYRange((ServerLevel) (Object) this),
                McMathUtilKt.getDEFAULT_WORLD_GRAVITY(),
                AerodynamicUtils.DEFAULT_SEA_LEVEL,
                AerodynamicUtils.DEFAULT_MAX
            );
        }
    }

    @Inject(method = "getPoiManager", at = @At("HEAD"))
    void onGetPoiManager(CallbackInfoReturnable<PoiManager> cir) {
        if (chunkSource.getPoiManager() instanceof final OfLevel levelProvider) {
            levelProvider.setLevel((ServerLevel) (Object) this);
        }
    }

    @Inject(method = "isCloseToVillage", at = @At("HEAD"), cancellable = true)
    void preIsCloseToVillage(BlockPos blockPos, int i, CallbackInfoReturnable<Boolean> cir) {
        if (i <= 6) {
            final boolean[] found = {false};
            VSGameUtilsKt.transformToNearbyShipsAndWorld(ServerLevel.class.cast(this), blockPos.getX(), blockPos.getY(), blockPos.getZ(), i * 100.0, (double x, double y, double z) -> {
                found[0] = found[0] || this.sectionsToVillage(SectionPos.of(BlockPos.containing(x, y, z))) <= i;
            });
            if (found[0]) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Include ships in particle distance check. Seems to only be used by /particle
     */
    @WrapOperation(
        method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean includeShipsInParticleDistanceCheck(
        final BlockPos player, final Position particle, final double distance,
        final Operation<Boolean> closerToCenterThan) {

        final ServerLevel self = ServerLevel.class.cast(this);
        final LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(
            self, (int) particle.x() >> 4, (int) particle.z() >> 4);

        if (ship == null) {
            // vanilla behaviour
            return closerToCenterThan.call(player, particle, distance);
        }

        // in-world position
        final Vector3d posInWorld = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(particle));

        return posInWorld.distanceSquared(player.getX(), player.getY(), player.getZ()) < distance * distance;
    }

    @Unique
    private void vs$loadChunk(@NotNull final ChunkAccess worldChunk, final List<VsiTerrainUpdate> voxelShapeUpdates) {
        // Remove the chunk pos from vs$chunksToUnload if its present
        vs$chunksToUnload.remove(worldChunk.getPos().pack());
        if (!vs$knownChunks.containsKey(worldChunk.getPos())) {
            final List<Vector3ic> voxelChunkPositions = new ArrayList<>();

            final int chunkX = worldChunk.getPos().x();
            final int chunkZ = worldChunk.getPos().z();

            final LevelChunkSection[] chunkSections = worldChunk.getSections();

            for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                final LevelChunkSection chunkSection = chunkSections[sectionY];
                final Vector3ic chunkPos =
                    new Vector3i(chunkX, worldChunk.getSectionYFromSectionIndex(sectionY), chunkZ);
                voxelChunkPositions.add(chunkPos);

                if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                    // Add this chunk to the ground rigid body
                    final VsiTerrainUpdate voxelShapeUpdate =
                        VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                    voxelShapeUpdates.add(voxelShapeUpdate);

                    // region Detect wings
                    final ServerLevel thisAsLevel = ServerLevel.class.cast(this);
                    final LoadedServerShip
                        ship = VSGameUtilsKt.getLoadedShipManagingPos(thisAsLevel, chunkX, chunkZ);
                    if (ship != null) {
                        // Sussy cast, but I don't want to expose this directly through the vs-core api
                        final WingManager shipAsWingManager = ship.getWingManager();
                        final MutableBlockPos mutableBlockPos = new MutableBlockPos();
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    final BlockState blockState = chunkSection.getBlockState(x, y, z);
                                    final int posX = (chunkX << 4) + x;
                                    final int posY = worldChunk.getMinY() + (sectionY << 4) + y;
                                    final int posZ = (chunkZ << 4) + z;
                                    if (blockState.getBlock() instanceof WingBlock) {
                                        mutableBlockPos.set(posX, posY, posZ);
                                        final Wing wing =
                                            ((WingBlock) blockState.getBlock()).getWing(thisAsLevel,
                                                mutableBlockPos, blockState);
                                        if (wing != null) {
                                            shipAsWingManager.setWing(shipAsWingManager.getFirstWingGroupId(),
                                                posX, posY, posZ, wing);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // endregion
                } else {
                    final VsiTerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                        .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                    voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                }
            }
            vs$knownChunks.put(worldChunk.getPos(), voxelChunkPositions);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        final ServerLevel self = ServerLevel.class.cast(this);
        final VsiServerShipWorld shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(self);
        // Find newly loaded chunks
        final ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) chunkSource.chunkMap;

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>();
        final DistanceManagerAccessor distanceManagerAccessor = (DistanceManagerAccessor) chunkSource.chunkMap.getDistanceManager();

        // Physics only needs the terrain around ships. Turning every loaded chunk into physics terrain cost a lot of
        // time and memory even in worlds without a single ship.
        final String dimension = VSGameUtilsKt.getDimensionId(self);
        final List<int[]> shipAreas = vs$shipTerrainAreas(shipObjectWorld, dimension);
        // Only the chunks physics needs get looked at, not every loaded chunk, which on a server with players spread
        // out is thousands of chunks every tick
        // Turned off in added-features.toml, physics gets every loaded chunk like Valkyrien Skies 2.4.11. In 26.x the
        // chunks around players carry no ticket of their own, so then being fully loaded is enough.
        final boolean onlyNearShips = AddedFeatures.getTerrainOnlyNearShips();
        final LongOpenHashSet needed = onlyNearShips
            ? vs$chunksNeededByPhysics(shipObjectWorld, dimension, shipAreas)
            : new LongOpenHashSet(chunkMapAccessor.getVisibleChunkMap().keySet());
        if (AddedFeatures.getGroundLoadedUnderShips()) ShipTerrainTickets.tick(self, shipObjectWorld, dimension);

        final LongIterator neededIterator = needed.iterator();
        while (neededIterator.hasNext()) {
            final long key = neededIterator.nextLong();
            final ChunkPos holderPos = ChunkPos.unpack(key);
            if (vs$knownChunks.containsKey(holderPos)) continue;
            final ChunkHolder chunkHolder = chunkMapAccessor.callGetVisibleChunkIfPresent(key);
            if (chunkHolder == null) continue;
            // Only load chunks that haven't been loaded before, and have a ticket
            if (!onlyNearShips || !distanceManagerAccessor.getTicketStorage().getTickets(key).isEmpty()) {
                // Loaded is enough, physics only needs the blocks. Chunks further from players than the simulation
                // distance never tick, and those are often under the far end of a long ship.
                final ChunkResult<LevelChunk> worldChunkResult =
                    chunkHolder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK);
                if (worldChunkResult.isSuccess()) {
                    // Only load chunks that have a ticket
                    final LevelChunk worldChunk = worldChunkResult.orElse(null);
                    vs$loadChunk(worldChunk, voxelShapeUpdates);
                }
            }
        }

        final Iterator<Entry<ChunkPos, List<Vector3ic>>> knownChunkPosIterator = vs$knownChunks.entrySet().iterator();
        while (knownChunkPosIterator.hasNext()) {
            final Entry<ChunkPos, List<Vector3ic>> knownChunkPosEntry = knownChunkPosIterator.next();
            final long chunkPos = knownChunkPosEntry.getKey().pack();
            // Unload chunks if they don't have tickets, if they're not in the visible chunks, or no ship is near anymore
            final ChunkPos knownPos = knownChunkPosEntry.getKey();
            if (!needed.contains(chunkPos)
                || (onlyNearShips && distanceManagerAccessor.getTicketStorage().getTickets(chunkPos).isEmpty())
                || chunkMapAccessor.callGetVisibleChunkIfPresent(chunkPos) == null) {
                final long ticksWaitingToUnload = vs$chunksToUnload.getOrDefault(chunkPos, 0L);
                if (ticksWaitingToUnload > VS$CHUNK_UNLOAD_THRESHOLD) {
                    // Unload this chunk
                    for (final Vector3ic unloadedChunk : knownChunkPosEntry.getValue()) {
                        final VsiTerrainUpdate deleteVoxelShapeUpdate =
                            getVsCore().newDeleteTerrainUpdate(unloadedChunk.x(), unloadedChunk.y(), unloadedChunk.z());
                        voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                    }
                    knownChunkPosIterator.remove();
                    vs$chunksToUnload.remove(chunkPos);
                } else {
                    vs$chunksToUnload.put(chunkPos, ticksWaitingToUnload + 1);
                }
            } else if (!vs$chunksToUnload.isEmpty()) {
                // Needed again, a ship coming back must not find its terrain gone a moment later
                vs$chunksToUnload.remove(chunkPos);
            }
        }

        // Send new loaded chunks updates to the ship world
        shipObjectWorld.addTerrainUpdates(
            VSGameUtilsKt.getDimensionId(self),
            voxelShapeUpdates
        );

        if (VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
            ValkyrienSkiesMod.splitHandler.tick(ServerLevel.class.cast(this));
        }

        DragInfoReporter.INSTANCE.tick((ServerLevel) (Object) this);

    }

    // Chunks of terrain kept around a ship, plus as far as it goes in a second at its speed
    @Unique
    private static final int VS$TERRAIN_MARGIN_CHUNKS = 2;
    @Unique
    private static final double VS$TERRAIN_LOOKAHEAD_SECONDS = 1.0;

    /** The chunk areas around this dimension's ships, as min x, min z, max x, max z. */
    @Unique
    private static List<int[]> vs$shipTerrainAreas(final VsiServerShipWorld shipWorld, final String dimension) {
        if (shipWorld.getLoadedShips().isEmpty()) return List.of();
        final List<int[]> areas = new ArrayList<>();
        for (final LoadedServerShip ship : shipWorld.getLoadedShips()) {
            if (!dimension.equals(ship.getChunkClaimDimension())) continue;
            final org.joml.primitives.AABBdc box = ship.getWorldAABB();
            final int margin = VS$TERRAIN_MARGIN_CHUNKS
                + (int) Math.ceil(ship.getVelocity().length() * VS$TERRAIN_LOOKAHEAD_SECONDS / 16.0);
            areas.add(new int[] {
                ((int) Math.floor(box.minX()) >> 4) - margin, ((int) Math.floor(box.minZ()) >> 4) - margin,
                ((int) Math.floor(box.maxX()) >> 4) + margin, ((int) Math.floor(box.maxZ()) >> 4) + margin
            });
        }
        return areas;
    }

    /**
     * Every chunk physics needs in this dimension: the chunks of every ship, and the terrain near ships. Nothing while
     * no ship is loaded at all.
     */
    @Unique
    private static LongOpenHashSet vs$chunksNeededByPhysics(final VsiServerShipWorld shipWorld, final String dimension,
        final List<int[]> areas) {
        final LongOpenHashSet needed = new LongOpenHashSet();
        if (shipWorld.getLoadedShips().isEmpty()) return needed;
        for (final int[] area : areas) {
            for (int x = area[0]; x <= area[2]; x++) {
                for (int z = area[1]; z <= area[3]; z++) needed.add(ChunkPos.pack(x, z));
            }
        }
        for (final ServerShip ship : shipWorld.getAllShips()) {
            if (!dimension.equals(ship.getChunkClaimDimension())) continue;
            ship.getActiveChunksSet().forEach((x, z) -> needed.add(ChunkPos.pack(x, z)));
        }
        return needed;
    }

    @Override
    public boolean isChunkKnownToPhysics(final int chunkX, final int chunkZ) {
        return vs$knownChunks.containsKey(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public void removeChunk(final int chunkX, final int chunkZ) {
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        vs$knownChunks.remove(chunkPos);
    }
}
