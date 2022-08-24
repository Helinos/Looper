package net.helinos.looper

import org.bukkit.entity.Player

class PUOffset(private var x: Int, private var z: Int) {

    val xOffset = WORLD_BORDER * 2 * x
    val zOffset = WORLD_BORDER * 2 * z
    val xChunk = xOffset / 16
    val zChunk = zOffset / 16

    fun add(addX: Int, addZ: Int) {
        this.x += addX
        this.z += addZ
    }

    fun isZero(): Boolean {
        return (this.x == 0 && this.z == 0)
    }
}