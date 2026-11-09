package org.valkyrienskies.mod.client.render;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When drawing a ship goes wrong, the ship is left out of that frame instead of crashing the game. This logs the first
 * time it happens, so the log doesn't fill up with one message a frame.
 */
public final class ShipRenderProblems {
    private static final Logger LOGGER = LoggerFactory.getLogger("Valkyrien Skies");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private ShipRenderProblems() {
    }

    public static void report(final String what, final RuntimeException e) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.error(what + ", leaving it out of the frame. Only the first time is logged.", e);
        }
    }
}
