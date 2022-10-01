package me.jumpwatch.webserver.php.linux;

import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.php.linux.utils.PHP;
import org.bukkit.plugin.java.JavaPlugin;

public class LinuxPHPNginxCore {


    public static void StartLinuxPHP() {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        if (main.getConfig().getBoolean("Settings.EnablePHP")) {
            LinuxPHPNginxCore web = new LinuxPHPNginxCore();
            web.fileCode();
            web.inlineCode();
            web.fileFunctionCode();
        }
    }
    private void inlineCode(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        PHP php = new PHP().snippet("");
        main.getLogger().info("Inline Code: " + php.toString());
    }
    private void fileCode(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        main.getLogger().info("Remote URL: " + new PHP(main.getDataFolder() + "/php/index.php"));
    }
    private void fileFunctionCode(){
        PHP php = new PHP().snippet("");
    }
}
