package net.helinos.looper

import com.comphenix.protocol.events.PacketContainer
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.packs.repository.Pack
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.math.abs

object BorderDisplay {
    fun chunk(packet: PacketContainer, player: Player): PacketContainer {
        val world = player.world

        val chunkInts = packet.integers
        val chunkX = chunkInts.readSafely(0)
        val chunkZ = chunkInts.readSafely(1)

        if (abs(chunkX) >= WB_CHUNK_CORD || abs(chunkZ) >= WB_CHUNK_CORD) {
            val nmsWorld = (world as CraftWorld).handle

            var getChunkX = chunkX
            var getChunkZ = chunkZ

            if (chunkX >= WB_CHUNK_CORD) getChunkX -= (WB_CHUNK_CORD * 2)
            if (chunkX < -WB_CHUNK_CORD) getChunkX += (WB_CHUNK_CORD * 2)
            if (chunkZ >= WB_CHUNK_CORD) getChunkZ -= (WB_CHUNK_CORD * 2)
            if (chunkZ < -WB_CHUNK_CORD) getChunkZ += (WB_CHUNK_CORD * 2)

            val nmsChunk = nmsWorld.getChunk(getChunkX, getChunkZ)
            val nmsPacket = ClientboundLevelChunkWithLightPacket(nmsChunk, nmsWorld.lightEngine, null as BitSet?, null as BitSet?, true, false)

            val newPacket = PacketContainer.fromPacket(nmsPacket)
            newPacket.integers.write(0, chunkX)
            newPacket.integers.write(1, chunkZ)

            return  newPacket
        }

        return  packet
    }

    fun unloadCheck(packet: PacketContainer): Boolean {
        val chunkInts = packet.integers
        val x = abs(chunkInts.readSafely(0))
        val z = abs(chunkInts.readSafely(1) * 16)
        Logger
        Logger.info("$INSIDE_RENDER, $x, $OUTSIDE_RENDER")
        return (x in INSIDE_RENDER..OUTSIDE_RENDER || z in INSIDE_RENDER..OUTSIDE_RENDER)
    }
}