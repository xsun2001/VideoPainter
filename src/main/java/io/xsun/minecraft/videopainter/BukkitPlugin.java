package io.xsun.minecraft.videopainter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitPlugin extends JavaPlugin {



    // Only handle vpainter
    // /vpainter region x y z
    // /vpainter image series index
    // /vpainter
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        assert (command.getName().equalsIgnoreCase("vpainter"));

        return false;
    }

    @Override
    public PluginCommand getCommand(String name) {
        return super.getCommand(name);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }
}