package me.jumpwatch.webserver.php.linux;

import me.jumpwatch.webserver.WebCoreProxy;
import me.jumpwatch.webserver.utils.DebugLogger;
import me.jumpwatch.webserver.utils.WPLogger;
import net.md_5.bungee.api.ProxyServer;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 03-08-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class PHPWebServerBun{
    private static Logger logger = Logger.getLogger("WebPluginPHPWebServerBun");
    WebCoreProxy main;
    public PHPWebServerBun(WebCoreProxy main) {
        this.main = main;
    }
    public void start(){
        if (checkConfigBeforeStart()){
            logger.info("Starting PHP Web Server");
            startphpfpm();
            startnginxfpm();
        }
    }
    private boolean checkConfigBeforeStart(){
        int FPMPort = main.configuration.getInt("Settings.LocalFPM");
        int PHPPort = main.configuration.getInt("Settings.PHPPort");
        File path = new File(main.getDataFolder() + "/phplinux/bin/php8/etc/php-fpm.d/www.conf");
        File path1 = new File(main.getDataFolder() + "/phplinux/bin/php8/etc/php-fpm.conf");
        File path2 = new File(main.getDataFolder() + "/nginxlinux/bin/nginx/conf/nginx.conf");
        File path3 = new File(main.getDataFolder() + "/nginxlinux/bin/nginx/conf/fastcgi.conf");
        File path4 = new File(main.getDataFolder() + "/nginxlinux/bin/nginx/conf/fastcgi_params");
        String ServerPath = main.configuration.getString("Settings.ServerLocation");
        String IndexLocation = main.getDataFolder() + "/php" + main.configuration.getString("Settings.IndexLocation");
        String ServerIP = main.configuration.getString("Settings.ServerIP");
        String fpmd = ServerPath + main.getDataFolder() + "/phplinux/bin/php8/etc/php-fpm.d/*.conf";
        String ServerSoftware = main.getDescription().getName() + "-" + main.getDescription().getVersion();
        try {
            updatePortInConfig(path, FPMPort);
            updatePathConfig(path1, fpmd);
            updateEntireNGINXConfig(path2, PHPPort, FPMPort, IndexLocation, ServerIP, main, ServerPath);
            updateCGIConfig(path3, ServerSoftware);
            updateCGIParms(path4, ServerSoftware);
            return true;
        } catch (Exception e) {
            if (main.configuration.getBoolean("Settings.debug")) DebugLogger.error(e.getMessage());
            return false;
        }
    }
    private static void updateCGIParms(File configFile, String Serversoftware) throws IOException {
        String newConfig =
                "\n" +
                        "fastcgi_param  QUERY_STRING       $query_string;\n" +
                        "fastcgi_param  REQUEST_METHOD     $request_method;\n" +
                        "fastcgi_param  CONTENT_TYPE       $content_type;\n" +
                        "fastcgi_param  CONTENT_LENGTH     $content_length;\n" +
                        "\n" +
                        "fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;\n" +
                        "fastcgi_param  REQUEST_URI        $request_uri;\n" +
                        "fastcgi_param  DOCUMENT_URI       $document_uri;\n" +
                        "fastcgi_param  DOCUMENT_ROOT      $document_root;\n" +
                        "fastcgi_param  SERVER_PROTOCOL    $server_protocol;\n" +
                        "fastcgi_param  REQUEST_SCHEME     $scheme;\n" +
                        "fastcgi_param  HTTPS              $https if_not_empty;\n" +
                        "\n" +
                        "fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;\n" +
                        "fastcgi_param  SERVER_SOFTWARE    " + Serversoftware + ";\n"+
                        "\n" +
                        "fastcgi_param  REMOTE_ADDR        $remote_addr;\n" +
                        "fastcgi_param  REMOTE_PORT        $remote_port;\n" +
                        "fastcgi_param  SERVER_ADDR        $server_addr;\n" +
                        "fastcgi_param  SERVER_PORT        $server_port;\n" +
                        "fastcgi_param  SERVER_NAME        $server_name;\n" +
                        "\n" +
                        "# PHP only, required if PHP was built with --enable-force-cgi-redirect\n" +
                        "fastcgi_param  REDIRECT_STATUS    200;\n";
        // Write the new configuration to the file
        Files.write(configFile.toPath(), newConfig.getBytes());
    }
    private static void updateCGIConfig(File configFile, String Serversoftware) throws IOException {
        String newConfig =
                "\n"+
                        "fastcgi_param  SCRIPT_FILENAME    $document_root$fastcgi_script_name;\n" +
                        "fastcgi_param  QUERY_STRING       $query_string;\n"+
                        "fastcgi_param  REQUEST_METHOD     $request_method;\n"+
                        "fastcgi_param  CONTENT_TYPE       $content_type;\n"+
                        "fastcgi_param  CONTENT_LENGTH     $content_length;\n"+
                        "\n"+
                        "fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;\n"+
                        "fastcgi_param  REQUEST_URI        $request_uri;\n"+
                        "fastcgi_param  DOCUMENT_URI       $document_uri;\n"+
                        "fastcgi_param  DOCUMENT_ROOT      $document_root;\n"+
                        "fastcgi_param  SERVER_PROTOCOL    $server_protocol;\n"+
                        "fastcgi_param  REQUEST_SCHEME     $scheme;\n"+
                        "fastcgi_param  HTTPS              $https if_not_empty;\n"+
                        "\n"+
                        "fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;\n"+
                        "fastcgi_param  SERVER_SOFTWARE    " + Serversoftware + ";\n"+
                        "\n"+
                        "fastcgi_param  REMOTE_ADDR        $remote_addr;\n"+
                        "fastcgi_param  REMOTE_PORT        $remote_port;\n"+
                        "fastcgi_param  SERVER_ADDR        $server_addr;\n"+
                        "fastcgi_param  SERVER_PORT        $server_port;\n"+
                        "fastcgi_param  SERVER_NAME        $server_name;\n"+
                        "\n"+
                        "# PHP only, required if PHP was built with --enable-force-cgi-redirect\n"+
                        "fastcgi_param  REDIRECT_STATUS    200;\n"+
                        "\n";
        // Write the new configuration to the file
        Files.write(configFile.toPath(), newConfig.getBytes());
    }
    private static void updatePortInConfig(File configFile, int newPort) throws IOException {
        // Read all lines of the config file
        List<String> lines = Files.readAllLines(configFile.toPath());

        // Define the pattern to find
        String searchPattern = "listen = 127.0.0.1:";

        // Iterate through each line and find the one to update
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith(searchPattern)) {
                // Replace the port in the found line
                lines.set(i, searchPattern + newPort);
            }
        }

        // Write the modified lines back to the file
        Files.write(configFile.toPath(), lines);
    }
    private static void updatePathConfig(File configFile, String fullpath) throws IOException {
        // Read all lines of the config file
        List<String> lines = Files.readAllLines(configFile.toPath());

        // Define the pattern to find
        String searchPattern = "include=";

        // Iterate through each line and find the one to update
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith(searchPattern)) {
                // Replace the port in the found line
                lines.set(i, searchPattern + fullpath);
            }
        }

        // Write the modified lines back to the file
        Files.write(configFile.toPath(), lines);
    }
    private static void updateEntireNGINXConfig(File configFile, int newListenPort, int newPhpFpmPort, String newDocumentRoot, String ServerIP, WebCoreProxy main, String ServerPath) throws IOException {
        // Define the new configuration template
        String newConfig =
                "#user  nobody;\n" +
                        "#Sorry but currently you cannot change this file. IT WILL BE OVERRIDDEN. This allows me to always update the file if you change config.yml \n" +
                        "worker_processes  1;\n" +
                        "error_log " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/error.log;\n" +
                        "error_log " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/error.log  notice;\n" +
                        "error_log " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/error.log  info;\n" +
                        "pid       " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/nginx.pid;\n" +
                        "\n" +
                        "events {\n" +
                        "    worker_connections  1024;\n" +
                        "}\n" +
                        "\n" +
                        "http {\n" +
                        "    client_body_temp_path " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/client_body_temp;\n" +
                        "    proxy_temp_path " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/proxy_temp;\n" +
                        "    fastcgi_temp_path " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/fastcgi_temp;\n" +
                        "    uwsgi_temp_path " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/uwsgi_temp;\n" +
                        "    scgi_temp_path " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/scgi_temp;\n" +
                        "    include       mime.types;\n" +
                        "    default_type  application/octet-stream;\n" +
                        "\n" +
                        "    # Enable logging\n" +
                        "    access_log " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/access.log;\n" +
                        "    access_log " + ServerPath + main.getDataFolder() + "/nginxlinux/bin/nginx/logs/access.log;\n" +
                        "\n" +
                        "    sendfile        on;\n" +
                        "    keepalive_timeout  65;\n" +
                        "\n" +
                        "    # Server block for handling requests\n" +
                        "    server {\n" +
                        "        listen 0.0.0.0:" + newListenPort + ";\n" +  // Replace the listen port
                        "        server_name " + ServerIP + ";\n" +
                        "\n" +
                        "        # Specify the document root where index files are located\n" +
                        "        root " + ServerPath + newDocumentRoot + ";\n" +  // Replace the document root
                        "\n" +
                        "        # List of index files that Nginx will try to serve if a directory is requested\n" +
                        "        index index.php index.html index.htm;\n" +
                        "        charset utf-8;\n" +
                        "\n" +
                        "        location / {\n" +
                        "            try_files $uri $uri/ /index.php?$query_string;\n" +
                        "        }\n" +
                        "\n" +
                        "        # Pass PHP requests to PHP-FPM for processing\n" +
                        "        location ~ \\.php$ {\n" +
                        "            fastcgi_split_path_info ^(.+\\.php)(/.+)$;\n" +
                        "            fastcgi_pass 127.0.0.1:" + newPhpFpmPort + ";\n" +  // Replace the PHP-FPM port
                        "            include fastcgi_params;\n" +
                        "            fastcgi_param PHP_VALUE \"upload_max_filesize = 100M \\n post_max_size=100M\";\n" +
                        "            fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;\n" +
                        "            fastcgi_intercept_errors off;\n" +
                        "            fastcgi_buffer_size 16k;\n" +
                        "            fastcgi_buffers 4 16k;\n" +
                        "            fastcgi_connect_timeout 300;\n" +
                        "            fastcgi_send_timeout 300;\n" +
                        "            fastcgi_read_timeout 300;\n" +
                        "        }\n" +
                        "        location ~ /\\.ht {\n" +
                        "            deny all;\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";

        // Write the new configuration to the file
        Files.write(configFile.toPath(), newConfig.getBytes());
    }
    private void startphpfpm(){
        ProxyServer.getInstance().getScheduler().schedule(main, () -> {
            String[] cmd = {
                    "/bin/sh", "-c",
                    "cd ~/plugins/webplugin/phplinux/bin/php8/sbin/ \n" +
                            "./php-fpm -p ~/" + main.getDataFolder() + "/phplinux/bin/php8 \n"
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
                    output.append(line).append("\n");
                }

                // Read any errors
                while ((line = stdError.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = p.waitFor();
                WPLogger.info("Exit code: " + exitCode);
                WPLogger.info("Output: " + output.toString());

            } catch (IOException | InterruptedException e) {
                if (main.configuration.getBoolean("Settings.debug")) DebugLogger.error(e.getMessage());
            } finally {
                if (p != null) {
                    p.destroy();
                }
            }
        }, 1l, TimeUnit.MILLISECONDS);
    }
    private void startnginxfpm(){
        ProxyServer.getInstance().getScheduler().schedule(main, () -> {
            String[] cmd = {
                    "/bin/sh", "-c",
                    "cd ~/plugins/webplugin/nginxlinux/bin/nginx/sbin/ \n" +
                            "./nginx -c ~/" + main.getDataFolder() + "/nginxlinux/bin/nginx/conf/nginx.conf"
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
                    output.append(line).append("\n");
                }

                // Read any errors
                while ((line = stdError.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = p.waitFor();
                WPLogger.info("Exit code: " + exitCode);
                WPLogger.info("Output: " + output.toString());

            } catch (IOException | InterruptedException e) {
                if (main.configuration.getBoolean("Settings.debug")) DebugLogger.error(e.getMessage());
            } finally {
                if (p != null) {
                    p.destroy();
                }
            }
        }, 1l, TimeUnit.MILLISECONDS);

    }
}
