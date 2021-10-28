package me.jumpwatch.webserver.php.linux.installers;


import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.utils.CheckOS;
import org.bukkit.plugin.java.JavaPlugin;

public class LinuxInstaller {
    public static void LinuxPHPNginxInstaller(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        WebCore.FilesPermissionsCheckWindows(main);
        LinuxPHPGetter.LinuxPHPGetter();
        LinuxPHPGetter.LinuxNginxGetter();
        if (CheckOS.isUnix()){
            Linuxsetup.setup();
        }
    }
}
