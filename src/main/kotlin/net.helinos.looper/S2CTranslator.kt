package net.helinos.looper

import com.comphenix.protocol.PacketType.Play.*
import com.comphenix.protocol.events.InternalStructure
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.injector.temporary.TemporaryPlayer
import com.comphenix.protocol.wrappers.BlockPosition
import com.comphenix.protocol.wrappers.Converters
import com.comphenix.protocol.wrappers.WrappedWatchableObject
import com.comphenix.protocol.wrappers.nbt.NbtBase
import com.comphenix.protocol.wrappers.nbt.NbtCompound
import net.minecraft.core.BlockPos
import org.bukkit.entity.Player
import java.util.*


object S2CTranslator {

    fun outgoing(packet: PacketContainer, player: Player) {
        if (player is TemporaryPlayer) {
            return
        }

        val offset = Looper.playerPUOffset[player.uniqueId]
        if (offset == null) {
            Logger.error("Started translating a S2C packet for a player with no offset!")
            return
        }

        when (packet.type) {
            Server.VIEW_CENTRE,
            Server.UNLOAD_CHUNK -> sendChunk(packet, offset, false, false)
            Server.MAP_CHUNK -> sendChunk(packet, offset, true, false)
            Server.LIGHT_UPDATE -> sendChunk(packet, offset, false, true)
            Server.MULTI_BLOCK_CHANGE -> sendChunkUpdate(packet, offset)
            Server.EXPLOSION -> sendExplosion(packet, offset)
            Server.POSITION -> sendPosition(packet, offset)
            Server.WORLD_PARTICLES,
            Server.NAMED_ENTITY_SPAWN,
            Server.SPAWN_ENTITY,
            Server.SPAWN_ENTITY_EXPERIENCE_ORB,
            Server.ENTITY_TELEPORT -> sendDouble(packet, offset)
            Server.ENTITY_METADATA -> sendMetadata(packet, offset)
            // TODO: Server.WINDOW_ITEMS -> {}
            // TODO: Server.SET_SLOT -> {}
            Server.TILE_ENTITY_DATA -> sendTileEntityData(packet, offset)
            Server.BLOCK_ACTION,
            Server.BLOCK_BREAK_ANIMATION,
            Server.BLOCK_CHANGE,
            Server.WORLD_EVENT,
            Server.SPAWN_POSITION,
            Server.OPEN_SIGN_EDITOR -> sendBlockPosition(packet, offset)
            Server.NAMED_SOUND_EFFECT,
            Server.CUSTOM_SOUND_EFFECT -> {
                packet.integers.modify(0) { curr_x: Int? -> curr_x?.plus(offset.xOffset shl 3) }
                packet.integers.modify(2) { curr_z: Int? -> curr_z?.plus(offset.zOffset shl 3) }
            }
            else -> {
                Logger.error("Unhandled S2C packet: ${packet.type.name()}")
            }
        }
    }

    private fun sendBlockPosition(packet: PacketContainer, offset: PUOffset) {
        if (packet.blockPositionModifier.size() > 0) {
            packet.blockPositionModifier.modify(0) { pos: BlockPosition? ->
                pos?.add(BlockPosition(offset.xOffset, 0, offset.zOffset))
            }
        } else {
            Logger.error("sendBlockPosition() Packet size error")
        }
    }

    private fun sendTileEntityData(packet: PacketContainer, offset: PUOffset) {
        sendBlockPosition(packet, offset)
        packet.nbtModifier.modify(0) { nbtBase: NbtBase<*>? ->
            if (nbtBase == null) return@modify null
            if (nbtBase is NbtCompound) {
                val nbt = nbtBase.deepClone() as NbtCompound
                if (nbt.containsKey("x") && nbt.containsKey("z")) {
                    nbt.put("x", nbt.getInteger("x") + offset.xOffset)
                    nbt.put("z", nbt.getInteger("z") + offset.zOffset)
                }
                return@modify nbt
            } else {
                return@modify nbtBase
            }
        }
    }

    private fun sendMetadata(packet: PacketContainer, offset: PUOffset) {
        if (packet.watchableCollectionModifier.size() > 0) {
            packet.watchableCollectionModifier.modify(0) { WROs: List<WrappedWatchableObject?>? ->
                if (WROs == null) return@modify null
                val result = ArrayList<WrappedWatchableObject?>(WROs.size)
                    for (WRO in WROs) {
                        if (WRO == null) {
                            result.add(null)
                            continue
                        }

                        val oldValue = WRO.value
                        if (oldValue is Optional<*>) {
                            if (oldValue.isPresent) {
                                val value = oldValue.get()
                                if (value is BlockPos) {
                                    WRO.value = Optional.of(value.offset(offset.xOffset, 0, offset.zOffset))
                                }
                            }
                        } else if (oldValue is BlockPosition) {
                            WRO.value = oldValue.add(BlockPosition(offset.xOffset, 0, offset.zOffset))
                        }
                        result.add(WRO)
                    }
                return@modify result
            }
        }
    }

    private fun sendPosition(packet: PacketContainer, offset: PUOffset) {
        var isRelativeX = false
        var isRelativeZ = false
        val items = packet.getSets(Converters.passthrough(Enum::class.java)).read(0)
        for (item in items) {
            when (item.name) {
                "X" -> isRelativeX = true
                "Z" -> isRelativeZ = true
            }
        }
        if (!isRelativeX || !isRelativeZ) {
            if (packet.doubles.size() > 2) {
                if (!isRelativeX) {
                    packet.doubles.modify(0) { x ->
                        if (x == null) null
                        else x + offset.xOffset }
                }
                if (!isRelativeZ) {
                    packet.doubles.modify(2) { z ->
                        if (z == null) null
                        else z + offset.zOffset
                    }
                }
            } else {
                Logger.error("sendPosition() Packet size error")
            }
        }
    }

    private fun sendChunk(
        packet: PacketContainer,
        offset: PUOffset,
        includeEntities: Boolean,
        includeLight: Boolean
    ) {
        packet.integers.modify(0) { curr_x: Int? -> curr_x?.plus(offset.xChunk) }
        packet.integers.modify(1) { curr_z: Int? -> curr_z?.plus(offset.zChunk) }
        if (includeLight) {
            val byteArrays = packet.structures.read(0).getLists(
                Converters.passthrough(
                    ByteArray::class.java
                )
            )
            for (i in 0..1) {
                byteArrays.modify(i) { list: List<ByteArray>? ->
                    if (list == null) return@modify null
                    val newList = ArrayList(list)
                    newList.replaceAll { bytes: ByteArray -> bytes.copyOf(bytes.size) }
                    return@modify newList
                }
            }
        }
        if (includeEntities) {
            packet.structures.read(0).getLists(InternalStructure.getConverter()).modify(0) { entities: List<InternalStructure>? ->
                if (entities == null) return@modify null
                for (entity in entities) {
                    entity.integers.modify(0) { packedXZ: Int ->
                        val x: Int = (packedXZ shr 4) + offset.xOffset
                        val y: Int = (packedXZ and 15) + offset.zOffset
                        return@modify x and 15 shl 4 or (y and 15)
                    }
                }
                return@modify entities
            }
        }
    }

    private fun sendChunkUpdate(packet: PacketContainer, offset: PUOffset) {
        val sp = packet.sectionPositions.read(0) ?: return
        packet.sectionPositions.write(0, sp.add(BlockPosition(offset.xChunk, 0, offset.zChunk)))
    }

    private fun sendExplosion(packet: PacketContainer, offset: PUOffset) {
        sendDouble(packet, offset)
        packet.blockPositionCollectionModifier.modify(0) { lst: List<BlockPosition>? ->
            if (lst == null) return@modify null
            val newLst = ArrayList<BlockPosition>(lst.size)
            for (blockPosition in lst) {
                newLst.add(blockPosition.add(BlockPosition(offset.xOffset, 0, offset.zOffset)))
            }
            return@modify newLst
        }
    }

    private fun sendDouble(packet: PacketContainer, offset: PUOffset) {
        if (packet.doubles.size() > 2) {
            packet.doubles.modify(0) { x: Double? -> x?.plus(offset.xOffset) }
            packet.doubles.modify(2) { z: Double? -> z?.plus(offset.zOffset) }
        } else {
            Logger.error("sendDouble() Packet size error")
        }
    }

}