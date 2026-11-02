package org.valkyrienskies.mod.util

import com.mojang.serialization.Codec
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.nbt.CompoundTag
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc

fun CompoundTag.putVector3d(prefix: String, vector3d: Vector3dc) =
    with(vector3d) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
    }

fun CompoundTag.getVector3d(prefix: String): Vector3d? {
    return if (
        !this.contains(prefix + "x") ||
        !this.contains(prefix + "y") ||
        !this.contains(prefix + "z")
    ) {
        null
    } else {
        Vector3d(
            this.getDoubleOr(prefix + "x", 0.0),
            this.getDoubleOr(prefix + "y", 0.0),
            this.getDoubleOr(prefix + "z", 0.0)
        )
    }
}

fun CompoundTag.putQuatd(prefix: String, quat: Quaterniondc) =
    with(quat) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
        putDouble(prefix + "w", w())
    }

fun CompoundTag.getQuatd(prefix: String): Quaterniond? =
    if (
        this.contains(prefix + "x") &&
        this.contains(prefix + "y") &&
        this.contains(prefix + "z") &&
        this.contains(prefix + "w")
    ) {
        Quaterniond(
            getDoubleOr(prefix + "x", 0.0),
            getDoubleOr(prefix + "y", 0.0),
            getDoubleOr(prefix + "z", 0.0),
            getDoubleOr(prefix + "w", 0.0),
        )
    } else null

fun ValueOutput.putVector3d(prefix: String, vector3d: Vector3dc) =
    with(vector3d) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
    }

fun ValueOutput.putQuatd(prefix: String, quat: Quaterniondc) =
    with(quat) {
        putDouble(prefix + "x", x())
        putDouble(prefix + "y", y())
        putDouble(prefix + "z", z())
        putDouble(prefix + "w", w())
    }

private fun ValueInput.readDouble(key: String): Double? = read(key, Codec.DOUBLE).orElse(null)

fun ValueInput.getVector3d(prefix: String): Vector3d? {
    val x = readDouble(prefix + "x") ?: return null
    val y = readDouble(prefix + "y") ?: return null
    val z = readDouble(prefix + "z") ?: return null
    return Vector3d(x, y, z)
}

fun ValueInput.getQuatd(prefix: String): Quaterniond? {
    val x = readDouble(prefix + "x") ?: return null
    val y = readDouble(prefix + "y") ?: return null
    val z = readDouble(prefix + "z") ?: return null
    val w = readDouble(prefix + "w") ?: return null
    return Quaterniond(x, y, z, w)
}
