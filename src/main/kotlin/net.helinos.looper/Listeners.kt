package net.helinos.looper

import org.bukkit.craftbukkit.v1_19_R1.CraftWorld
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import kotlin.math.abs


class Listeners : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player

        val x = event.to.x
        val z = event.to.z

        if (abs(x) >= WORLD_BORDER || abs(z) >= WORLD_BORDER) {
            var toX = x
            var toZ = z

            if (x >= WORLD_BORDER) {
                toX -= (WORLD_BORDER * 2)
                createOrAddOffset(player, 1, 0)
            }
            if (x <= -WORLD_BORDER) {
                toX += (WORLD_BORDER * 2)
                createOrAddOffset(player, -1, 0)
            }
            if (z >= WORLD_BORDER) {
                toZ -= (WORLD_BORDER * 2)
                createOrAddOffset(player, 0, 1)
            }
            if (z <= -WORLD_BORDER) {
                toZ += (WORLD_BORDER * 2)
                createOrAddOffset(player, 0, -1)
            }
            if (Looper.playerPUOffset[player.uniqueId]!!.isZero()) {
                Looper.playerPUOffset.remove(player.uniqueId)
                Logger.info("Dropped ${player.name} for leaving the PU")
            }

            // Using the nms teleport seems to hitch the player less
            val nmsPlayer = (player as CraftPlayer).handle
            val nmsWorld = (player.world as CraftWorld).handle
            nmsPlayer.teleportTo(nmsWorld, toX, event.to.y, toZ, event.to.yaw + 1080, event.to.pitch)
        }
    }

    private fun createOrAddOffset(player: Player, x: Int, z: Int) {
        if (Looper.playerPUOffset.containsKey(player.uniqueId)) {
            Looper.playerPUOffset[player.uniqueId]!!.add(x, z)
            Logger.info("Updated ${player.name}")
        } else {
            Looper.playerPUOffset[player.uniqueId] = PUOffset(x, z)
            Logger.info("Added ${player.name}")
        }
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        Looper.playerPUOffset.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        Looper.playerPUOffset.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val yaw = event.to.yaw
        if (yaw <= 180) {
            Looper.playerPUOffset.remove(event.player.uniqueId)
        }

    }
}