package xyz.usbpc.mc.spigot.plugin.teleport

import org.bukkit.plugin.java.JavaPlugin

class Main: JavaPlugin() {

    override fun onEnable() {
        println("Hello world, I'm a plugin!")
    }

    override fun onDisable() {
        println("I'm sorry to see you go so soon :(")
    }
}