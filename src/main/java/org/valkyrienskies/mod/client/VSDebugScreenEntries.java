package org.valkyrienskies.mod.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/**
 * The F3 ship lines. Minecraft builds the overlay out of registered entries now.
 */
public final class VSDebugScreenEntries {
    private static final Identifier SHIP_COUNT = id("ship_count");
    private static final Identifier DRAGGED_BY = id("dragged_by");
    private static final Identifier TARGETED_SHIP = id("targeted_ship");
    private static final List<Identifier> ALL = List.of(SHIP_COUNT, DRAGGED_BY, TARGETED_SHIP);
    private static boolean registered = false;

    private VSDebugScreenEntries() {
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath("valkyrienskies", path);
    }

    public static List<Identifier> register() {
        if (!registered) {
            registered = true;
            DebugScreenEntries.register(SHIP_COUNT, new ShipCount());
            DebugScreenEntries.register(DRAGGED_BY, new DraggedBy());
            DebugScreenEntries.register(TARGETED_SHIP, new TargetedShip());
        }
        return ALL;
    }

    private static final class ShipCount implements DebugScreenEntry {
        @Override
        public void display(final DebugScreenDisplayer displayer, final @Nullable Level level,
            final @Nullable LevelChunk clientChunk, final @Nullable LevelChunk serverChunk) {
            if (level instanceof final ServerLevel serverLevel) {
                final var world = VSGameUtilsKt.getShipObjectWorld(serverLevel);
                displayer.addLine("Ships Loaded: " + world.getLoadedShips().size() + "/" + world.getAllShips().size());
            }
        }
    }

    private static final class DraggedBy implements DebugScreenEntry {
        @Override
        public void display(final DebugScreenDisplayer displayer, final @Nullable Level level,
            final @Nullable LevelChunk clientChunk, final @Nullable LevelChunk serverChunk) {
            final var player = Minecraft.getInstance().player;
            if (player == null || level == null) {
                return;
            }
            final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) player).getDraggingInformation();
            if (info != null && info.isEntityBeingDraggedByAShip()) {
                final Long shipId = info.getLastShipStoodOn();
                if (shipId != null) {
                    final Ship ship = VSGameUtilsKt.getAllShips(level).getById(shipId);
                    if (ship != null) {
                        displayer.addLine("Dragged by: " + ship.getSlug());
                    }
                }
            }
        }
    }

    private static final class TargetedShip implements DebugScreenEntry {
        @Override
        public void display(final DebugScreenDisplayer displayer, final @Nullable Level level,
            final @Nullable LevelChunk clientChunk, final @Nullable LevelChunk serverChunk) {
            final HitResult hitResult = Minecraft.getInstance().hitResult;
            if (level == null || !(hitResult instanceof final BlockHitResult blockHitResult)) {
                return;
            }
            final BlockPos blockPos = blockHitResult.getBlockPos();
            final Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
            if (ship == null) {
                return;
            }
            final LoadedServerShip lsship =
                level instanceof final ServerLevel serverLevel ? VSGameUtilsKt.getLoadedShipManagingPos(serverLevel, blockPos) : null;
            displayer.addToGroup(TARGETED_SHIP, ChatFormatting.UNDERLINE + "Targeted Ship: " + ship.getSlug());
            if (lsship != null) {
                // Info about physical properties is only visible in singleplayer or while hosting an integrated server.
                displayer.addToGroup(TARGETED_SHIP, "Static: " + lsship.isStatic());
                displayer.addToGroup(TARGETED_SHIP, "Mass: " + lsship.getInertiaData().getMass() + " kg");
            }
            final Vector3dc scale = ship.getTransform().getShipToWorldScaling();
            displayer.addToGroup(TARGETED_SHIP, String.format(Locale.ROOT, "Ship Scale: %.3f / %.3f / %.3f", scale.x(), scale.y(), scale.z()));
            final Vector3dc linVel = ship.getVelocity();
            displayer.addToGroup(TARGETED_SHIP, String.format(Locale.ROOT, "Linear Velocity: %.3f / %.3f / %.3f, total: %.3f m/s",
                linVel.x(), linVel.y(), linVel.z(), linVel.length()));
            final Vector3dc angVel = ship.getOmega();
            displayer.addToGroup(TARGETED_SHIP, String.format(Locale.ROOT, "Angular Velocity: %.3f / %.3f / %.3f", angVel.x(), angVel.y(), angVel.z()));
            final Vec3 pos = VSGameUtilsKt.toWorldCoordinates(ship, Vec3.atCenterOf(blockPos));
            displayer.addToGroup(TARGETED_SHIP, String.format(Locale.ROOT, "Targeted World Position: %.3f / %.3f / %.3f", pos.x(), pos.y(), pos.z()));
        }
    }
}
