# VS Core and Krunch

`vs-core-bundle-cf208d8b56.jar` goes in this folder to build the mod. It isn't part of this source code.

The bundle holds VS Core, the Krunch physics engine and the libraries they need, all unchanged from the official Valkyrien Skies 2.4.11 release. VS Core and Krunch are closed source and belong to the Valkyrien Skies team, so they aren't published here and aren't covered by this project's license.

To make the bundle, take the official Valkyrien Skies 2.4.11 Fabric jar and remove the parts that are in this source or come from somewhere else: `org/valkyrienskies/mod`, `assets`, `data`, `META-INF/jars`, `META-INF/maven`, the mod's json files, and the logging, Kotlin and annotation libraries that Minecraft and Fabric already provide. Save what's left as `vs-core-bundle-cf208d8b56.jar`.
