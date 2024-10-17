package me.jumpwatch.webserver.php.linux;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 29-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class PhpInstaller {
    private static Logger logger = Logger.getLogger("WebPluginPHPInstaller");
    public static void installphp() {
        String[] cmd = {
                "/bin/sh", "-c",
                "mkdir ~/plugins/webplugin/phplinux \n" +
                        "cd ~/plugins/webplugin/phplinux \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/WebPlugin-linux-PHP8.2-PRECOMPILED.tar.gz \n" +
                        "tar -xzvf WebPlugin-linux-PHP8.2-PRECOMPILED.tar.gz \n" +
                        "cp ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.conf.default ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.conf \n" +
                        "cp ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.d/www.conf.default ~/plugins/webplugin/phplinux/bin/php8/etc/php-fpm.d/www.conf \n" +
                        "mkdir ~/plugins/webplugin/phplinux/bin/php8/log \n" +
                        "touch ~/plugins/webplugin/phplinux/bin/php8/log/php-fpm.log \n" +
                        "cd /usr/lib/x86_64-linux-gnu/ \n" +
                        "wget https://github.com/hypersmc/WebPluginV2Repo/raw/main/libonig.so.5 \n" +
                        "export LD_LIBRARY_PATH=/home/container/plugins/webplugin/phplinux/bin/php8/lib:$LD_LIBRARY_PATH\n"
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
                logger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                logger.warning(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            logger.info("Process exited with code: " + exitCode);
            FilePermissions();
            installnginx();
        } catch (IOException | InterruptedException e) {
//            logger.severe(e.getMessage());
            e.printStackTrace();
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
                        "mkdir ~/plugins/webplugin/nginxlinux/nginx/logs \n" +
                        "touch ~/plugins/webplugin/nginxlinux/nginx/logs/error.log \n"
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
                logger.info(line);
                output.append(line).append("\n");
            }

            // Read any errors
            while ((line = stdError.readLine()) != null) {
                logger.warning(line);
                output.append(line).append("\n");
            }

            int exitCode = p.waitFor();
            logger.info("Process exited with code: " + exitCode);
            FilePermissionsNGINX();

        } catch (IOException | InterruptedException e) {
//            logger.severe(e.getMessage());
            e.printStackTrace();
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
//    public static void test(){
//        int listeningport = (int) WebCoreProxyVel.settings.get("PHPPort");
//        String documentRoot = WebCoreProxyVel.dataFolder + "/php/index.php";
//        String[] cmd = {
//                "/bin/sh", "-c",
//                "cd ~/php/bin/php8/bin/ \n" +
//                "./php " + "~/plugins/webplugin/php/index.php"
//        };
//
//        Process p = null;
//        try {
//            p = Runtime.getRuntime().exec(cmd);
//
//            // Read output streams
//            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
//            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//
//            StringBuilder output = new StringBuilder();
//            String line;
//            while ((line = stdInput.readLine()) != null) {
//                logger.info(line);
//                output.append(line).append("\n");
//            }
//
//            // Read any errors
//            while ((line = stdError.readLine()) != null) {
//                logger.warning(line);
//                output.append(line).append("\n");
//            }
//
//            int exitCode = p.waitFor();
//            logger.info("Process exited with code: " + exitCode);
//        } catch (IOException | InterruptedException e) {
//            logger.severe(e.getMessage());
//            e.printStackTrace();
//        } finally {
//            if (p != null) {
//                p.destroy();
//            }
//        }
//
//    }
    public static void FilePermissionsNGINX(){
        File php = new File("plugins/webplugin/nginxlinux/bin/nginx/sbin/nginx");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            logger.info("File permission check success!");
        } catch (Exception e) {
            logger.severe("Failed to check file permission. Please enable debug mode.");

        }
    }
    public static void FilePermissions(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/bin/php");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            logger.info("File permission check success!");
            FilePermissionsfpm();
        } catch (Exception e) {
            logger.severe("Failed to check file permission. Please enable debug mode.");

        }
    }
    public static void FilePermissionsfpm(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/sbin/php-fpm");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            logger.info("File permission check success!");
            FolderPermissions();
        } catch (Exception e) {
            logger.severe("Failed to check file permission. Please enable debug mode.");

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
                throw new RuntimeException(ex);
            }
            e.printStackTrace();
        }
    }
}
