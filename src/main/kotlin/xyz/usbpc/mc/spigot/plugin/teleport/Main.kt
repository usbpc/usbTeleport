package xyz.usbpc.mc.spigot.plugin.teleport

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.TimeUnit

class Main : JavaPlugin() {

    override fun onEnable() {
        println("Hello world, I'm a plugin!")
        server.pluginManager.registerEvents(MyListener(), this)
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
                        return false
                    }
                    requests.first().let { request ->
                        request.cancelJob.cancel()
                        request.requester.teleport(sender, PlayerTeleportEvent.TeleportCause.COMMAND)
                        sender.sendMessage("You accepted the teleport request from ${ChatColor.YELLOW}${request.requester.displayName}${ChatColor.WHITE}!")
                        request.requester.sendMessage("${ChatColor.YELLOW}${sender.displayName}${ChatColor.WHITE} accepted your teleport request!")
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
                        return false
                    }
                    requests.first().let { request ->
                        request.cancelJob.cancel()
                        sender.sendMessage("You denied the teleport request from ${ChatColor.YELLOW}${request.requester.displayName}${ChatColor.WHITE}!")
                        request.requester.sendMessage("${ChatColor.YELLOW}${sender.displayName}${ChatColor.WHITE} denied your teleport request.")
                        requests.remove(request)
                    }
                    if (requests.isEmpty()) {
                        activeRequests.remove(sender.uniqueId)
                    }
                }
            }
            else -> {
                val target: Player? = sender.server.getPlayerExact(args[0])
                if (target == null) {
                    sender.sendMessage("Please specify a valid user!")
                    return true
                }
                if (sender.uniqueId == target.uniqueId) {
                    sender.sendMessage("You can't send a request to yourself!")
                    return true
                }
                sender.sendMessage("A teleport request was send to ${ChatColor.YELLOW}${target.displayName}${ChatColor.WHITE}, it will expire in 120 seconds.")
                target.sendMessage("${ChatColor.YELLOW}${sender.displayName}${ChatColor.WHITE} is requesting to teleport to you.\n" +
                                           "Accept with ${ChatColor.GREEN}/tpa accept${ChatColor.WHITE} or deny with ${ChatColor.RED}/tpa deny${ChatColor.WHITE}.\n" +
                                           "This request will be active for 120 seconds.")
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
                                        sender.sendMessage(
                                                "${ChatColor.YELLOW}${target.displayName}${ChatColor.WHITE} didn't respond in time. You will not be teleported!")
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