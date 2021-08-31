package me.jumpwatch.webserver.php.linux.installers;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class LinuxPHPGetter {
    public static void LinuxPHPGetter(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String url = "https://www.php.net/distributions/php-8.0.10.tar.gz";
        try {
            File dir = new File(main.getDataFolder() + "/tmpfiles/");
            dir.mkdirs();
            download(url, main.getDataFolder() + "/tmpfiles/" + "php.tar.gz");
            main.getLogger().info("Linux PHP downloaded!");
            LinuxPHPUnzipper.LinuxPHPUnzipper();
        } catch (IOException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }
    public static void LinuxNginxGetter(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String url = "http://nginx.org/download/nginx-1.20.1.tar.gz";
        try {
            File dir = new File(main.getDataFolder() + "/tmpfiles/");
            dir.mkdirs();
            download(url, main.getDataFolder() + "/tmpfiles/" + "nginx.tar.gz");
            main.getLogger().info("Linux Nginx downloaded!");
            LinuxPHPUnzipper.LinuxNginxUnzipper();
        } catch (IOException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    private static void download(String urld, String location) throws IOException {
        URL url = new URL(urld);
        BufferedInputStream inputStream = new BufferedInputStream(url.openStream());
        FileOutputStream outputStream = new FileOutputStream(location);
        byte[] buffer = new byte[1024];
        int count=0;
        while ((count = inputStream.read(buffer,0,1024)) != -1){
            outputStream.write(buffer, 0, count);
        }
        inputStream.close();
        outputStream.close();
    }
}
