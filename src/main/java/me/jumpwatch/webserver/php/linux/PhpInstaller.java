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
                        "tar -xzvf WebPlugin-linux-PHP8.2-PRECOMPILED.tar.gz \n"
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
    public static void FilePermissions(){
        File php = new File("plugins/webplugin/phplinux/bin/php8/bin/php");
        try {
            php.setExecutable(true, false);
            php.setReadable(true, false);
            php.setWritable(true, false);
            logger.info("File permission check success!");
        } catch (Exception e) {
            logger.severe("Failed to check file permission. Please enable debug mode.");

        }
    }
}
