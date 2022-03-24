package me.jumpwatch.webserver.php.windows;

import me.jumpwatch.webserver.utils.ConfigChanger;
import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class WindowsPHPNginxCore {

    public static void StopWindowsNginxandPHP(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String Nginx = main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/nginx.exe -s quit -p " + main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/";
        String PHP = "CMD /C taskkill /f /IM php.exe /T";
        String line;
        try {
            Process PNginx = Runtime.getRuntime().exec(Nginx);
            Process PPHP = Runtime.getRuntime().exec(PHP);
            if (main.getConfig().getBoolean("Settings.debug")){
                BufferedReader bri = new BufferedReader(new InputStreamReader(PNginx.getInputStream()));
                BufferedReader bre = new BufferedReader(new InputStreamReader(PPHP.getErrorStream()));
                while ((line = bri.readLine()) != null) {
                    System.out.println(line);
                }
                bri.close();
                while ((line = bre.readLine()) != null) {
                    System.out.println(line);
                }
                bre.close();
                PNginx.waitFor();
                System.out.println("Done.");
            }
            }catch (IOException | InterruptedException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }
    public static void reloadWindowsNginxandPHP(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String Nginx = main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/nginx.exe -s reload -p " + main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/";
        String PHPStop = "CMD /C taskkill /f /IM php.exe /T";
        String PHPStart = main.getDataFolder() + "/phpwindows/php/php.exe -S " + main.getConfig().getString("Settings.ServerIP") + ":"+ main.getConfig().getString("Settings.PHPPort") + " -t " + main.getDataFolder() + "/phpwindows/php/public";
        String line;
        try {
            Process PNginx = Runtime.getRuntime().exec(Nginx);
            Process PPHPSTOP = Runtime.getRuntime().exec(PHPStop);
            Process PPHPSTART = Runtime.getRuntime().exec(PHPStart);
            if (main.getConfig().getBoolean("Settings.debug")){
                BufferedReader bri = new BufferedReader
                        (new InputStreamReader(PNginx.getInputStream()));
                BufferedReader bre = new BufferedReader
                        (new InputStreamReader(PNginx.getErrorStream()));
                while ((line = bri.readLine()) != null) {
                    System.out.println(line);
                }
                bri.close();
                while ((line = bre.readLine()) != null) {
                    System.out.println(line);
                }
                bre.close();
                PNginx.waitFor();
                System.out.println("Done.");
            }
        }catch (IOException | InterruptedException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }
    public static void StartWindowsNginxandPHP(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        main.getLogger().info("Ensuring right permissions!");
        FilesPermissionsCheckWindows(main);
        main.getLogger().info("Ensuring that IP and Port is set!");
        ConfigChanger.Changeconf(main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/conf/nginx.conf", "localhost", main.getConfig().getString("Settings.ServerIP") + "");
        ConfigChanger.Changeconf(main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/conf/nginx.conf", "80", main.getConfig().getString("Settings.PHPPort") + "");
        main.getLogger().info("Success!");
        try {
            main.getLogger().info(main.getConfig().getString("Settings.ServerIP") + ":" + main.getConfig().getString("Settings.PHPPort"));
            File folder = new File(main.getDataFolder() + "/phpwindows/php/public/");
            folder.mkdirs();
            String Nginx = main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/nginx.exe -p " + main.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/";
            String PHP = main.getDataFolder() + "/phpwindows/php/php.exe -S " + main.getConfig().getString("Settings.ServerIP") + ":"+ main.getConfig().getString("Settings.PHPPort") + " -t " + main.getDataFolder() + "/phpwindows/php/public";
            Process PNginx = Runtime.getRuntime().exec(Nginx);
            main.getLogger().info("Attempting to start PHP");
            Process PPHP = Runtime.getRuntime().exec(PHP);
            main.getLogger().info("PHP Webserver started on:");
            main.getLogger().info(main.getConfig().getString("Settings.ServerIP") + ":" + main.getConfig().getString("Settings.PHPPort"));
            String line;
            if (main.getConfig().getBoolean("Settings.debug")){
                BufferedReader bri = new BufferedReader(new InputStreamReader(PNginx.getInputStream()));
                BufferedReader bre = new BufferedReader(new InputStreamReader(PNginx.getErrorStream()));
                BufferedReader bri2 = new BufferedReader(new InputStreamReader(PPHP.getInputStream()));
                BufferedReader bre2 = new BufferedReader(new InputStreamReader(PPHP.getErrorStream()));
                while ((line = bri.readLine()) != null) {
                    System.out.println(line);
                }
                while ((line = bri2.readLine()) != null) {
                    System.out.println(line);
                }
                bri.close();
                bri2.close();
                while ((line = bre.readLine()) != null) {
                    System.out.println(line);
                }
                while ((line = bre2.readLine()) != null) {
                    System.out.println(line);
                }
                bre.close();
                bre2.close();
                PNginx.waitFor();
                PPHP.waitFor();
                System.out.println("Done.");
            }

        } catch (IOException | InterruptedException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    private static void FilesPermissionsCheckWindows(WebCore core){
        File Nginx = new File(core.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/nginx.exe");
        File NginxFolder = new File(core.getDataFolder() + "/phpwindows/nginx/nginx-1.20.1/");
        File PHPCoreFolder = new File(core.getDataFolder() + "/phpwindows/");
        try {
            Nginx.setExecutable(true, false);
            Nginx.setReadable(true, false);
            Nginx.setWritable(true, false);
            NginxFolder.setExecutable(true, false);
            NginxFolder.setReadable(true, false);
            NginxFolder.setWritable(true, false);
            PHPCoreFolder.setExecutable(true, false);
            PHPCoreFolder.setReadable(true, false);
            PHPCoreFolder.setWritable(true, false);
            core.getLogger().info("File permission check success!");
        } catch (Exception e) {
            core.getLogger().info("Failed to check permissions! Please enable debug mode and report back to me (dev)");
            if (core.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

        }
    }
}
