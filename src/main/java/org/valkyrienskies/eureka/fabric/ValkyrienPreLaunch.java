package org.valkyrienskies.eureka.fabric;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * For now, just using this class as an abusive early entrypoint to run the updater
 */
public class ValkyrienPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        if (Boolean.getBoolean("vs.mixinAudit")) {
            audit();
        }
    }

    // Development only: applies every mixin to its target and reports all failures in one go
    private static void audit() {
        final ClassLoader loader = ValkyrienPreLaunch.class.getClassLoader();
        final java.util.Set<String> targets = new java.util.TreeSet<>();
        for (final String config : new String[] {"valkyrienskies-common.mixins.json", "valkyrienskies-fabric.mixins.json", "vs_eureka-common.mixins.json", "vs_eureka.mixins.json"}) {
            try (final java.io.InputStream in = loader.getResourceAsStream(config)) {
                if (in == null) {
                    continue;
                }
                final com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
                final String pkg = json.get("package").getAsString();
                for (final String key : new String[] {"mixins", "client"}) {
                    if (!json.has(key)) {
                        continue;
                    }
                    for (final com.google.gson.JsonElement name : json.getAsJsonArray(key)) {
                        final String path = (pkg + "." + name.getAsString()).replace('.', '/') + ".class";
                        try (final java.io.InputStream cls = loader.getResourceAsStream(path)) {
                            final org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                            new org.objectweb.asm.ClassReader(cls).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
                            for (final org.objectweb.asm.tree.AnnotationNode a : node.invisibleAnnotations) {
                                if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || a.values == null) {
                                    continue;
                                }
                                for (int i = 0; i < a.values.size(); i += 2) {
                                    if (a.values.get(i + 1) instanceof final java.util.List<?> list) {
                                        for (final Object entry : list) {
                                            targets.add(entry instanceof final org.objectweb.asm.Type type ? type.getClassName() : String.valueOf(entry));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (final java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        int failed = 0;
        for (final String target : targets) {
            try {
                Class.forName(target.replace('/', '.'), false, loader);
            } catch (final ClassNotFoundException e) {
                // Target belongs to an optional mod that is not installed
            } catch (final Throwable t) {
                failed++;
                Throwable root = t;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                System.out.println("AUDIT-FAIL " + target + " :: " + root.getClass().getSimpleName() + ": " + root.getMessage());
            }
        }
        System.out.println("AUDIT-DONE targets=" + targets.size() + " failed=" + failed);
        System.exit(0);
    }
}
