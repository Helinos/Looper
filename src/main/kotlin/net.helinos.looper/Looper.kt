package net.helinos.looper

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.PacketType.Play.*
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.*
import com.comphenix.protocol.injector.GamePhase
import com.comphenix.protocol.wrappers.nbt.NbtBase
import net.helinos.looper.Looper.Companion.NAME
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.math.abs


val Logger: Logger = LogManager.getLogger(NAME)

const val RENDER_DISTANCE = 16
var WORLD_BORDER = 20000
var WB_CHUNK_CORD = -1
var INSIDE_RENDER = -1
var OUTSIDE_RENDER = -1

lateinit var PLUGIN: Looper

class Looper : JavaPlugin() {

    companion object {
        const val NAME = "Looper"
        const val VERSION = "0.1.0"
        const val ALIAS = "$NAME $VERSION"
        var playerPUOffset: HashMap<UUID, PUOffset> = HashMap()
    }

    init {
        PLUGIN = this
    }

    override fun onEnable() {

        // Check if the world border is aligned with a chunk
        // Later we should try to integrate with the WorldBorder plugin in some way
        if (WORLD_BORDER < 1 || RENDER_DISTANCE < 1) {
            Logger.error("The world border and render distance need to be a positive numbers!")
            Bukkit.getPluginManager().disablePlugin(this)
        } else if (WORLD_BORDER % 16 != 0) {
            WORLD_BORDER -= WORLD_BORDER % 16
            Logger.info("The world border should be aligned with a chunk so it has been rounded down to $WORLD_BORDER")
        }

        INSIDE_RENDER = WORLD_BORDER - (RENDER_DISTANCE * 16)
        OUTSIDE_RENDER = WORLD_BORDER + (RENDER_DISTANCE * 16)
        if (INSIDE_RENDER <= 16) {
            Logger.error("Either the world border or the render distance is too small!")
            Bukkit.getPluginManager().disablePlugin(this)
        }

        WB_CHUNK_CORD = WORLD_BORDER / 16

        server.pluginManager.registerEvents(Listeners(), this)

        val pm = ProtocolLibrary.getProtocolManager()
        val packets = HashSet<PacketType>()

        // Outbound packets (Server -> Client)
        run {
            val paramsServer = PacketAdapter.params()
            paramsServer.plugin(this)
            paramsServer.connectionSide(ConnectionSide.SERVER_SIDE)
            paramsServer.listenerPriority(ListenerPriority.HIGHEST)
            paramsServer.gamePhase(GamePhase.PLAYING)

            packets.add(Server.BLOCK_ACTION)
            packets.add(Server.BLOCK_BREAK_ANIMATION)
            packets.add(Server.BLOCK_CHANGE)
            packets.add(Server.MULTI_BLOCK_CHANGE)
            packets.add(Server.MAP_CHUNK)
            packets.add(Server.UNLOAD_CHUNK)
            packets.add(Server.LIGHT_UPDATE)
            packets.add(Server.EXPLOSION)
            packets.add(Server.SPAWN_POSITION)
            packets.add(Server.RESPAWN)
            packets.add(Server.POSITION)
            packets.add(Server.WORLD_PARTICLES)
            packets.add(Server.WORLD_EVENT)
            packets.add(Server.NAMED_SOUND_EFFECT)
            packets.add(Server.CUSTOM_SOUND_EFFECT)
            packets.add(Server.NAMED_ENTITY_SPAWN)
            packets.add(Server.SPAWN_ENTITY)
            packets.add(Server.SPAWN_ENTITY_EXPERIENCE_ORB)
            packets.add(Server.ENTITY_TELEPORT)
            packets.add(Server.OPEN_SIGN_EDITOR)
            packets.add(Server.ENTITY_METADATA)
            packets.add(Server.VIEW_CENTRE)
            packets.add(Server.WINDOW_ITEMS)
            packets.add(Server.SET_SLOT)
            packets.add(Server.TILE_ENTITY_DATA)

            paramsServer.types(packets)

            pm.addPacketListener(object : PacketAdapter(paramsServer) {
                override fun onPacketSending(event: PacketEvent) {
                    val player = event.player

                    // Border Display
                    if (abs(player.location.x).toInt() in INSIDE_RENDER..OUTSIDE_RENDER || abs(player.location.z).toInt() in INSIDE_RENDER..OUTSIDE_RENDER) {
                        val packet = event.packet
                        var cancel = false

                        when (event.packet.type) {
                            Server.MAP_CHUNK -> event.packet = BorderDisplay.chunk(packet, player)
                            // Cancel the position sync when we start lying to them about their position
                            // This seems like a pretty hacky way of checking for this packet, but I can't think of any other way
                            Server.POSITION -> {
                                val yaw = packet.float.readSafely(0)
                                if (yaw >= 900) { // Listeners.kt teleports the player with + 1080 degrees of yaw
                                    Logger.info("Cancelled position sync packet")
                                    cancel = true
                                    val teleportID = packet.integers.readSafely(0)
                                    val fauxAccept = pm.createPacket(Client.TELEPORT_ACCEPT)
                                    fauxAccept.integers.write(0, teleportID)
                                    pm.receiveClientPacket(event.player, fauxAccept)
                                }
                            }
                            else -> {}
                        }

                        event.isCancelled = cancel
                    }

                    // Lie to player about position
                    if (event.player.uniqueId in playerPUOffset) {
                        val packet = when (event.packet.type) {
                            Server.TILE_ENTITY_DATA -> cloneTileEntityData(event.packet)
                            Server.MAP_CHUNK -> cloneMapChunkEntitiesData(event.packet)
                            else -> event.packet.shallowClone()
                        }

                        S2CTranslator.outgoing(packet, player);
                        event.packet = packet
                    }
                }
            })
        }

        // Inbound packets (Client -> Server)
        run {
            val paramsClient = PacketAdapter.params()
            paramsClient.plugin(this)
            paramsClient.connectionSide(ConnectionSide.CLIENT_SIDE)
            paramsClient.listenerPriority(ListenerPriority.LOWEST)
            paramsClient.gamePhase(GamePhase.PLAYING)

            packets.clear()

            packets.add(Client.POSITION)
            packets.add(Client.POSITION_LOOK)
            packets.add(Client.BLOCK_DIG)
            packets.add(Client.USE_ITEM)
            packets.add(Client.VEHICLE_MOVE)
            packets.add(Client.SET_COMMAND_BLOCK)
            packets.add(Client.SET_JIGSAW)
            packets.add(Client.STRUCT)
            packets.add(Client.UPDATE_SIGN)

            paramsClient.types(packets)

            pm.addPacketListener(object : PacketAdapter(paramsClient) {
                override fun onPacketReceiving(event: PacketEvent) {
                    if (event.player.uniqueId in playerPUOffset) {
                        try {
                            C2STranslator.incoming(event.packet, event.player)
                        } catch (e: UnsupportedOperationException) {
                            event.isCancelled = true
                            e.printStackTrace()
                            if (event.player != null) {
                                Logger.error("Failed: " + event.packet.type.name())
                            }
                        }
                    }
                }
            })
        }
    }

    private fun cloneTileEntityData(packet: PacketContainer): PacketContainer {
        val packet = packet.shallowClone()
        for ((i, obj) in packet.nbtModifier.values.withIndex()) {
            if (obj == null) continue
            packet.nbtModifier.write(i, obj.deepClone())
        }
        return packet
    }

    private fun cloneMapChunkEntitiesData(packet: PacketContainer): PacketContainer {
        val packet = packet.shallowClone()
        for ((i, obj) in packet.listNbtModifier.values.withIndex()) {
            val newList = ArrayList<NbtBase<*>>(obj.size)
            for (nbtBase in obj) {
                newList.add(nbtBase.deepClone())
            }
            packet.listNbtModifier.write(i, newList)
        }
        return packet
    }

    override fun onDisable() {
        HandlerList.unregisterAll(this)
        Logger.info("$ALIAS disabled")
    }
}