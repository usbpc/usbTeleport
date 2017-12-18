package xyz.usbpc.mc.spigot.plugin.teleport

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.NoSuchElementException

enum class Permissions(val permission: Permission) {

    PERMISSION_BASE(Permission("usbteleport").apply {
        default = PermissionDefault.TRUE
    }),
    PERMISSION_COMMAND(Permission("usbteleport.command").apply {
        addParent(PERMISSION_BASE.permission, false)
    }),
    COMMAND_TPA(Permission("usbteleport.command.tpa").apply {
        addParent(PERMISSION_COMMAND.permission, false)
    }),
    COMMAND_TPA_REQUEST(Permission("usbteleport.command.tpa.request").apply {
        addParent(COMMAND_TPA.permission, false)
    })
}

class Main : JavaPlugin() {
    override fun onLoad() {
        server.pluginManager.apply {
            Permissions.values().forEach {
                addPermission(it.permission)
            }
        }
    }

    override fun onEnable() {
        println("---    OP Permissions---")
        server.pluginManager.getDefaultPermissions(false).forEach {
            println(it.name)
        }
        println("---NON-OP Permissions---")
        server.pluginManager.getDefaultPermissions(true).forEach {
            println(it.name)
        }
        println("Hello world, I'm a plugin!")
        //server.pluginManager.registerEvents(MyListener(), this)
        this.getCommand("tpa").executor = TeleportCommands()
    }

    override fun onDisable() {
        println("I'm sorry to see you go so soon :(")
    }
}

class TeleportCommands : CommandExecutor {
    private val activeRequests: MutableMap<UUID, MutableList<Request>> = mutableMapOf()

    private data class Request(val requester: Player, val cancelJob: Job)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        //Only players are allowed to execute this command!
        if (sender !is Player) return false
        if (args.isEmpty()) return false
        when (args[0]) {
            "accept" -> {
                synchronized(activeRequests) {
                    val requests = activeRequests[sender.uniqueId]
                    if (requests == null) {
                        sender.sendMessage("There are currently no tpa requests for you!")
                        return true
                    }
                    val request = if (args.size < 2) {
                        requests.first()
                    } else {
                        try {
                            requests.first {it.requester.uniqueId == UUID.fromString(args[1])}
                        } catch (ex: NoSuchElementException) {
                            return true
                        }
                    }
                    request.let {request ->
                        request.requester.teleport(sender, PlayerTeleportEvent.TeleportCause.COMMAND)
                        request.cancelJob.cancel()
                        sender.sendMessage(
                                "You accepted the teleport request from ${ChatColor.YELLOW}${request.requester.displayName}${ChatColor.WHITE}!")
                        request.requester.sendMessage(
                                "${ChatColor.YELLOW}${sender.displayName}${ChatColor.WHITE} accepted your teleport request!")
                        requests.remove(request)
                    }
                    if (requests.isEmpty()) {
                        activeRequests.remove(sender.uniqueId)
                    }
                }
            }
            "deny" -> {
                synchronized(activeRequests) {
                    val requests = activeRequests[sender.uniqueId]
                    if (requests == null) {
                        sender.sendMessage("There are currently no tpa requests for you!")
                        return true
                    }
                    val request = if (args.size < 2) {
                        requests.first()
                    } else {
                        try {
                            requests.first {it.requester.uniqueId == UUID.fromString(args[1])}
                        } catch (ex: NoSuchElementException) {
                            return true
                        }
                    }
                    request.let {request ->
                        sender.sendMessage(
                                "You denied the teleport request from ${ChatColor.YELLOW}${request.requester.displayName}${ChatColor.WHITE}!")
                        request.requester.sendMessage(
                                "${ChatColor.YELLOW}${sender.displayName}${ChatColor.WHITE} denied your teleport request.")
                        request.cancelJob.cancel()
                        requests.remove(request)
                    }
                    if (requests.isEmpty()) {
                        activeRequests.remove(sender.uniqueId)
                    }
                }
            }
            else -> {
                //if (sender.hasPermission("usbpc.teleport.tpa.request")) return false
                val target: Player? = sender.server.getPlayerExact(args[0])
                if (target == null) {
                    sender.sendMessage("Please specify a valid user!")
                    return true
                }
                if (sender.uniqueId == target.uniqueId) {
                    sender.sendMessage("You can't send a request to yourself!")
                    return true
                }
                synchronized(activeRequests) {
                    activeRequests[target.uniqueId]?.let {
                        if (it.any {it.requester.uniqueId == sender.uniqueId}) {
                            sender.sendMessage("You can only send one request to that person!")
                            return true
                        }
                    }
                }
                val requesterMessage = TextComponent("A teleport request was send to ").apply {
                    addExtra(TextComponent(target.displayName).apply {color = ChatColor.YELLOW})
                    addExtra(TextComponent(", it will expire in 120 seconds."))
                }
                sender.spigot().sendMessage(requesterMessage)
                val targetMessage = TextComponent("").apply {
                    addExtra(TextComponent(sender.displayName).apply {color = ChatColor.YELLOW})
                    addExtra(" is requesting to teleport to you.\n")
                    addExtra("Accept with ")
                    val accept = TextComponent("/tpa accept").apply {
                        color = ChatColor.GREEN
                        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, arrayOf(TextComponent("Click to accept.")))
                        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa accept ${sender.uniqueId}")
                    }
                    addExtra(accept)
                    addExtra(" or deny with ")
                    val deny = TextComponent("/tpa deny").apply {
                        color = ChatColor.RED
                        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, arrayOf(TextComponent("Click to deny.")))
                        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpa deny ${sender.uniqueId}")
                    }
                    addExtra(deny)
                    addExtra(".\n")
                    addExtra("This request will be active for 120 seconds.")
                }
                target.spigot().sendMessage(targetMessage)
                //Hell I don't want race conditions!
                synchronized(activeRequests) {
                    //Lets either get the list of teleport requests for that user or create a brand new one!
                    activeRequests.getOrPut(target.uniqueId) {mutableListOf()}
                            //Add a request to the mix
                            .add(Request(sender, launch {
                                //After 120 seconds
                                delay(120, TimeUnit.SECONDS)
                                //If there is still an active request for that target with that user
                                synchronized(activeRequests) {
                                    activeRequests[target.uniqueId]?.let {list ->
                                        list.remove(list.single {it.requester.uniqueId == sender.uniqueId})
                                        val message = TextComponent(target.displayName).apply {
                                            color = ChatColor.YELLOW
                                            addExtra("didn't respond in time. You will not be teleported!")
                                        }
                                        sender.spigot().sendMessage(message)
                                        //If there are no longer any tp requests for a specific user remove the user from the map
                                        if (list.isEmpty()) {

                                            activeRequests.remove(target.uniqueId)
                                        }
                                    }
                                }
                            }))
                }
            }
        }
        return true
    }
}

class MyListener : Listener {
    @EventHandler
    fun onPlayerJoinEvent(event: PlayerJoinEvent) {
        event.player.sendMessage("Hello ${event.player.uniqueId}")
    }
}