rootProject.name = "worldedit"

include("worldedit-libs")

listOf("bukkit", "core", "sponge", "cli").forEach {
    include("worldedit-libs:$it")
    include("worldedit-$it")
}
// include("worldedit-mod")
include("worldedit-libs:core:ap")

include("worldedit-core:doctools")
