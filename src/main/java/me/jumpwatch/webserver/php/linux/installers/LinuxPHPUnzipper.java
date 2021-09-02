package me.jumpwatch.webserver.php.linux.installers;

import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.utils.TAR;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class LinuxPHPUnzipper {

    public static void LinuxPHPUnzipper(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String zipFilePath = main.getDataFolder() + "/tmpfiles/php.tar.gz";
        String destDir = main.getDataFolder() + "/phplinux/php/";
        try {
            Unzipper(zipFilePath, destDir);
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    public static void LinuxNginxUnzipper(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String zipFilePath = main.getDataFolder() + "/tmpfiles/nginx.tar.gz";
        String destDir = main.getDataFolder() + "/phplinux/nginx/";
        try {
            Unzipper(zipFilePath, destDir);
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    private static void Unzipper(String file, String output) {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        try {
            TAR.extractTarArchive(new File(file), output);
        } catch (IOException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }
}
