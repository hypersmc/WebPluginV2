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
            Linuxsetup.setup();
        }
    }
}
