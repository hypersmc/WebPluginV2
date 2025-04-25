package me.jumpwatch.webserver.utils;

import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

/**
 * @author JumpWatch on 30-01-2025
 * @Project WebPluginV2
 * v1.0.0
 */
public class util {



    public String detectSettingWebsite(Plugin pl){
        if(pl.getConfig().getBoolean("Settings.EnablePHP")){
            return "" + ChatColor.DARK_GRAY +"|      " + ChatColor.GREEN +"PHP System"+ ChatColor.RESET + "\n";
        }else{
            return "" + ChatColor.DARK_GRAY +"|      " + ChatColor.RED+"PHP System"+ ChatColor.RESET + "\n";
        }
    }
    public String settingsPHPPort(Plugin pl){
        return "" + ChatColor.DARK_GRAY +"|      " + ChatColor.RED+"Website Addon"+ ChatColor.RESET + "\n";
    }
}
