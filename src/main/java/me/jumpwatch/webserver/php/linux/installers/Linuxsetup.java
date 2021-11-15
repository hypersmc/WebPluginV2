package me.jumpwatch.webserver.php.linux.installers;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Linuxsetup {
    public static boolean donoproceed = false;
    public static void setup() throws IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        File folder = new File(main.getDataFolder() + "/phplinux/php/php-8.0.10/");

        main.getLogger().info("This setup of PHP will take a long time. Don't close the server if it seems unresponsive!");
        String linuxupdate = "apt -y update";
        String gcc = "apt -y install gcc";
        String pkg = "apt -y install pkg-config";
        String xml = "apt -y install libxml12-dev";
        String sqlite = "apt -y install libsqlite3-dev";
        String zliblg = "apt -y install zliblg-dev";
        String make = "apt -y install make";
        String sudo = "apt -y install sudo";
        main.getLogger().info("Trying to install every dependencies!");
        try{
            main.getLogger().info("Trying to install linux updates!");
            Process lu = Runtime.getRuntime().exec(linuxupdate);
            main.getLogger().info("Trying to install ggc!");
            Process ggcc = Runtime.getRuntime().exec(gcc);
            main.getLogger().info("Trying to install pkg-config!");
            Process pkgc = Runtime.getRuntime().exec(pkg);
            main.getLogger().info("Trying to install libxml12-dev!");
            Process xmllib = Runtime.getRuntime().exec(xml);
            main.getLogger().info("Trying to install libsqlite3-dev!");
            Process sqllite = Runtime.getRuntime().exec(sqlite);
            main.getLogger().info("Trying to install zliblg-dev!");
            Process zlib = Runtime.getRuntime().exec(zliblg);
            main.getLogger().info("Trying to install make!");
            Process maker = Runtime.getRuntime().exec(make);
            main.getLogger().info("Trying to install sudo!");
            Process sudos = Runtime.getRuntime().exec(sudo);
        } catch (Exception e) {
            main.getLogger().warning("FAILED to install dependencies");
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }

        if (folder.getUsableSpace() > 0) {
            runsetup();
        }
    }
    public static void runsetup() throws IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        ProcessBuilder processBuilder = new ProcessBuilder();
        ProcessBuilder processBuilder2 = new ProcessBuilder();

        processBuilder.command("sudo bash ./configure", "--enable-fpm", "--with-mysqli");
        processBuilder.directory(new File(main.getDataFolder() + "/phplinux/php/php-8.0.10/"));
        processBuilder2.command("sudo make install");
        processBuilder2.directory(new File(main.getDataFolder() + "/phplinux/php/php-8.0.10/"));
        main.getLogger().info(processBuilder.directory().getPath());
        try {
            Process p1 = processBuilder.start();
        } catch (IOException e) {
            main.getLogger().warning("FAILED to run php configuring");
            donoproceed = true;
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
        if (!donoproceed){
            try {
                Process p2 = processBuilder2.start();
            } catch (IOException e) {
                main.getLogger().warning("FAILED to run make install");
                donoproceed = true;
                if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
            }
        }
    }
}
