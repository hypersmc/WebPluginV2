package me.jumpwatch.webserver.php.windows.installers;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WindowsPHPUnzipper {
    private static final int BUFFER_SIZE = 4096;
    public static void WindowsNginxUnzipper(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String zipFilePath = main.getDataFolder() + "/tmpfiles/nginx.zip";
        String destDir = main.getDataFolder() + "/phpwindows/nginx/";
        try {
            unzip(zipFilePath, destDir);
            main.getLogger().info("Windows Nginx unzipped!");
        } catch (IOException e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    public static void WindowsPHPUnzipper(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String zipFilePath = main.getDataFolder() + "/tmpfiles/php.zip";
        String destDir = main.getDataFolder() + "/phpwindows/php/";
        try {
            unzip(zipFilePath, destDir);
            main.getLogger().info("Windows PHP unzipped!");
        } catch (IOException e) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    main.getLogger().info("Retrying Windows PHP unzipping!");
                    try {
                        unzip(zipFilePath, destDir);
                    } catch (IOException ex) {
                        if (main.getConfig().getBoolean("Settings.debug")) ex.printStackTrace();

                    }
                }
            }.runTaskLater(main, 100L);
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    /*
    .zip file unzipper
     */

    public static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}
