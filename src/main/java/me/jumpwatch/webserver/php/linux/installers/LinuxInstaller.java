package me.jumpwatch.webserver.php.linux.installers;


import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.utils.CheckOS;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class LinuxInstaller {
    public static void LinuxPHPNginxInstaller() throws IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        WebCore.FilesPermissionsCheckLinux(main);
        LinuxPHPGetter.LinuxPHPGetter();
        LinuxPHPGetter.LinuxNginxGetter();
        if (CheckOS.isUnix()){
            if (CheckOS.isRunningInsideDocker()) {
                Linuxsetup.setup();
            }else {
                main.getLogger().info("You are currently running " + main.getName() + " in a linux machine but not dockerd!");
                main.getLogger().info("For reasons you will NEED to run the server in a docker container so it can install everything.");
            }
        }
    }
}
