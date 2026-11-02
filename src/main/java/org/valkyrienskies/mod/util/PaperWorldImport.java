package org.valkyrienskies.mod.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Lets a Fabric server open a world that was made by Paper. Paper keeps these files per dimension where Fabric keeps
 * them for the whole world, and without them the world can't load. See https://docs.papermc.io/paper/migration/
 * <p>
 * Files are only ever copied, and only to a place that has no file yet. Nothing Paper wrote is changed or removed.
 */
public final class PaperWorldImport {
    private static final Logger LOGGER = LogManager.getLogger("VS2 Paper World Import");

    private static final String WORLD_GEN_SETTINGS = "world_gen_settings.dat";

    // The world gen settings go last, a copy cut short by a crash then simply runs again on the next start
    private static final String[] FILES = {
        "game_rules.dat", "scheduled_events.dat", "wandering_trader.dat", "weather.dat", WORLD_GEN_SETTINGS
    };

    private PaperWorldImport() {
    }

    public static void importIfNeeded(final Path worldFolder) {
        final Path fabricData = worldFolder.resolve("data/minecraft");
        final Path paperData = worldFolder.resolve("dimensions/minecraft/overworld/data/minecraft");
        if (Files.exists(fabricData.resolve(WORLD_GEN_SETTINGS)) || !Files.exists(paperData.resolve(WORLD_GEN_SETTINGS))) {
            return;
        }

        LOGGER.info("This world was made by Paper, copying its world settings to where Fabric reads them");
        try {
            Files.createDirectories(fabricData);
            for (final String file : FILES) {
                final Path from = paperData.resolve(file);
                final Path to = fabricData.resolve(file);
                if (Files.exists(from) && !Files.exists(to)) {
                    final Path temporary = fabricData.resolve(file + ".vs_import");
                    Files.copy(from, temporary, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporary, to, StandardCopyOption.ATOMIC_MOVE);
                    LOGGER.info("Copied {}", file);
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Could not copy the settings of this Paper world, it may fail to load", e);
        }
    }
}
