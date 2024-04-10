/*
 * ******************************************************
 *  *Copyright (c) 2020-2022. Jesper Henriksen mhypers@gmail.com
 *
 *  * This file is part of WebServer project
 *  *
 *  * WebServer can not be copied and/or distributed without the express
 *  * permission of Jesper Henriksen
 *  ******************************************************
 */ 
package me.jumpwatch.webserver.php.windows.installers;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class WindowsPHPGetter {
    public static void WindowsNginxGetter(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String url = "http://nginx.org/download/nginx-1.25.4.zip";
        try {
            File dir = new File(main.getDataFolder() + "/tmpfiles/");
            dir.mkdirs();
            download(url, main.getDataFolder() + "/tmpfiles/" + "nginx.zip");
            main.getLogger().info("Windows Nginx downloaded!");
            WindowsPHPUnzipper.WindowsNginxUnzipper();
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    public static void WindowsPHPGetter(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String url = "https://windows.php.net/downloads/releases/php-8.1.28-Win32-vs16-x86.zip";
        try {
            File dir = new File(main.getDataFolder() + "/tmpfiles/");
            dir.mkdirs();
            download(url, main.getDataFolder() + "/tmpfiles/" + "php.zip");
            main.getLogger().info("Windows PHP downloaded!");
            WindowsPHPUnzipper.WindowsPHPUnzipper();
        } catch (Exception e) {
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
