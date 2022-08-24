package net.helinos.looper

import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.PacketType.Play.*
import com.comphenix.protocol.events.InternalStructure
import com.comphenix.protocol.injector.temporary.TemporaryPlayer
import com.comphenix.protocol.wrappers.BlockPosition
import org.bukkit.entity.Player
import org.bukkit.util.Vector

object C2STranslator {

    fun incoming(packet: PacketContainer, player: Player?) {
        if (player == null || player is TemporaryPlayer) {
            return
        }

        val e = InternalStructure.getConverter()

        val offset = Looper.playerPUOffset[player.uniqueId]
        if (offset == null) {
            Logger.error("Started translating a C2S packet for a player with no offset!")
            return
        }

        when (packet.type) {
            Client.POSITION,
            Client.VEHICLE_MOVE,
            Client.POSITION_LOOK -> recvDouble(packet, offset);
            Client.STRUCT,
            Client.SET_JIGSAW,
            Client.SET_COMMAND_BLOCK,
            Client.UPDATE_SIGN,
            Client.BLOCK_DIG -> recvPosition(packet, offset)
            Client.USE_ITEM -> recvMovingPosition(packet, offset)
            else -> {
                Logger.error("Unhandled S2C packet: ${packet.type.name()}")
            }
        }
    }

    private fun recvDouble(packet: PacketContainer, offset: PUOffset) {
        if (packet.doubles.size() > 2) {
            packet.doubles.modify(0) { x: Double? ->
                if (x == null) null
                else x - offset.xOffset
            }
            packet.doubles.modify(2) { z: Double? ->
                if (z == null) null
                else z - offset.zOffset
            }
        } else {
            val size = packet.doubles.size()
            Logger.error("recvDouble() Packet size error: $size")
        }
    }

    private fun recvPosition(packet: PacketContainer, offset: PUOffset) {
        if (packet.blockPositionModifier.size() > 0) {
            packet.blockPositionModifier.modify(0) {pos ->
                return@modify pos?.subtract(BlockPosition(offset.xOffset, 0, offset.zOffset))
            }
        } else {
            Logger.error("recvPosition() Packet size error")
        }
    }

    private fun recvMovingPosition(packet: PacketContainer, offset: PUOffset) {
        val movBP = packet.movingBlockPositions.read(0) ?: return
        movBP.blockPosition = movBP.blockPosition.subtract(BlockPosition(offset.xOffset, 0, offset.zOffset))
        movBP.posVector = movBP.posVector.subtract(Vector(offset.xOffset, 0, offset.zOffset))
        packet.movingBlockPositions.write(0, movBP)
    }

}