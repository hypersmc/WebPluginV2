package me.jumpwatch.webserver.php.linux;


import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.utils.DebugLogger;
import me.jumpwatch.webserver.utils.WPLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 29-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class PhpInstaller {
    private static Logger logger = Logger.getLogger("WebPluginPHPInstaller");
    public static void installphp() {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String[] cmd = {
                "/bin/sh", "-c",
                "mkdir ~/plugins/webplugin/phplinux \n" +
                        "cd ~/plugins/webplugin/phplinux \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/"+ Objects.requireNonNullElse(main.getConfig().getString("Settings.GithubWebPluginPHPRepoFilename"), "WebPlugin-linux-PHP8.2-PRECOMPILED.tar.gz") + " \n" +
                        "tar -xzvf " + Objects.requireNonNullElse(main.getConfig().getString("Settings.GithubWebPluginPHPRepoFilename"), "WebPlugin-linux-PHP8.2-PRECOMPILED.tar.gz") + " \n" +
                        "cp ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.conf.default ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.conf \n" +
                        "cp ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.d/www.conf.default ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.d/www.conf \n" +
                        "mkdir ~/plugins/webplugin/phplinux/bin/php8/log \n" +
                        "touch ~/plugins/webplugin/phplinux/bin/php8/log/php-fpm.log \n"
        };
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);

            // Read output streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                WPLogger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                WPLogger.warn(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            WPLogger.info("Process exited with code: " + exitCode);
            FilePermissions();
            installnginx();
        } catch (IOException | InterruptedException e) {
            DebugLogger.error(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
    public static void installnginx(){
        String[] cmd = {
                "/bin/sh", "-c",
                "mkdir ~/plugins/webplugin/nginxlinux \n" +
                        "cd ~/plugins/webplugin/nginxlinux \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/WebPlugin-linux-NGINX-1.27.1-PRECOMPILED.tar.gz \n" +
                        "tar -xzvf WebPlugin-linux-NGINX-1.27.1-PRECOMPILED.tar.gz \n" +
                        "mkdir ~/plugins/webplugin/nginxlinux/bin/nginx/logs \n" +
                        "touch ~/plugins/webplugin/nginxlinux/bin/nginx/logs/error.log \n"
        };
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);

            // Read output streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                WPLogger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                WPLogger.warn(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            WPLogger.info("Process exited with code: " + exitCode);
            FilePermissionsNGINX();
            installglibc();
        } catch (IOException | InterruptedException e) {
            DebugLogger.error(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
    public static void installlibonig(){
        String[] cmd = {
                "/bin/sh", "-c",
                "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/ \n" +
                        "mkdir /home/container/plugins/webplugin/phplinux/bin/php8/lib/libonig/ \n" +
                        "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/libonig/ \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/WebPlugin-linux-LIBONIG-PRECOMPILED.tar.gz \n"+
                        "tar -xzvf WebPlugin-linux-LIBONIG-PRECOMPILED.tar.gz \n"
        };
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);

            // Read output streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                WPLogger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                WPLogger.warn(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            WPLogger.info("Process exited with code: " + exitCode);
            FilePermissionsLIBONIG();
            installlibz();
        } catch (IOException | InterruptedException e) {
            DebugLogger.error(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
    public static void installlibz(){
        String[] cmd = {
                "/bin/sh", "-c",
                "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/ \n" +
                        "mkdir /home/container/plugins/webplugin/phplinux/bin/php8/lib/libz/ \n" +
                        "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/libz/ \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/WebPlugin-linux-LIBZ-PRECOMPILED.tar.gz \n"+
                        "tar -xzvf WebPlugin-linux-LIBZ-PRECOMPILED.tar.gz \n"
        };
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);

            // Read output streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                WPLogger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                WPLogger.warn(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            WPLogger.info("Process exited with code: " + exitCode);
            FilePermissionsLIBZ();
        } catch (IOException | InterruptedException e) {
            DebugLogger.error(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
    public static void installglibc(){
        String[] cmd = {
                "/bin/sh", "-c",
                "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/ \n" +
                        "mkdir /home/container/plugins/webplugin/phplinux/bin/php8/lib/glibc/ \n" +
                        "cd /home/container/plugins/webplugin/phplinux/bin/php8/lib/glibc/ \n" +
                "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/WebPlugin-linux-GLIBC-PRECOMPILED.tar.gz \n"+
                "tar -xzvf WebPlugin-linux-GLIBC-PRECOMPILED.tar.gz \n"
        };
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmd);

            // Read output streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                WPLogger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                WPLogger.warn(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            WPLogger.info("Process exited with code: " + exitCode);
            FilePermissionsGLIBC();
            installlibonig();
        } catch (IOException | InterruptedException e) {
            DebugLogger.error(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    public static void FilePermissionsGLIBC(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/lib/glibc/lib/libm.so.6");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
            FolderPermissions();
        } catch (Exception e) {

            DebugLogger.error(e.getMessage());
            
        }
    }
    public static void FilePermissionsLIBZ(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/lib/libz/lib/libz.so.1.3.1");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
            FolderPermissions();
        } catch (Exception e) {
            DebugLogger.error(e.getMessage());

        }
    }
    public static void FilePermissionsLIBONIG(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/lib/libonig/lib/libonig.so.5.2.0");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
            FolderPermissions();
        } catch (Exception e) {
            DebugLogger.error(e.getMessage());

        }
    }
    public static void FilePermissionsNGINX(){
        File php = new File("plugins/webplugin/nginxlinux/bin/nginx/sbin/nginx");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
        } catch (Exception e) {
            DebugLogger.error(e.getMessage());

        }
    }
    public static void FilePermissions(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/bin/php");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
            FilePermissionsfpm();
        } catch (Exception e) {
            DebugLogger.error(e.getMessage());

        }
    }
    public static void FilePermissionsfpm(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/sbin/php-fpm");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            WPLogger.info("File permission check success!");
            FolderPermissions();
        } catch (Exception e) {
            DebugLogger.error(e.getMessage());

        }
    }
    public static void FolderPermissions(){
        try {
            Process userProcess = Runtime.getRuntime().exec("whoami");
            BufferedReader reader = new BufferedReader(new InputStreamReader(userProcess.getInputStream()));
            String currentUser = reader.readLine(); // Get the output (username)
            reader.close();
            String command = "chown -R " + currentUser + ": /home/container/plugins/webplugin/php";
            Process chownProcess = Runtime.getRuntime().exec(command);
            chownProcess.waitFor(); // Wait for the process to complete

            System.out.println("Command executed successfully");

        } catch (IOException | InterruptedException e) {
            try {
                String command = "chown -R www-data: /home/container/plugins/webplugin/php";
                Process chownProcess = Runtime.getRuntime().exec(command);
                chownProcess.waitFor(); // Wait for the process to complete

                System.out.println("Command executed successfully");
            } catch (Exception ex) {
                DebugLogger.error(ex.getMessage());
            }
            DebugLogger.error(e.getMessage());
        }
    }
}
