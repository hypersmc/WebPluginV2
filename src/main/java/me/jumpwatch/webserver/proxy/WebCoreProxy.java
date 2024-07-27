package me.jumpwatch.webserver.proxy;


import me.jumpwatch.webserver.html.NoneSSLHtmlProxyBun;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 25-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class WebCoreProxy extends Plugin {
    public Configuration configuration;
    public String pluginversion = "2.4R";
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private WebCoreProxy m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 10;
    public ContentTypeResolver resolver;
    private Logger logger = Logger.getLogger("WebPluginProxyBun");
    public static File dataFolder;
    @Override //All other (Bungeecord and such)
    public void onEnable() {
        dataFolder = getDataFolder();
        getLogger().info("WebPluginProxy enabled");
        try{
            makeConfig();
            configuration  = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException ignore) {}
        resolver = new ContentTypeResolver();
        resolver.loadContentTypesBun();
        this.shutdown = false;
        logger.info("Current OS: " + CheckOS.OS);
        logger.info("Is running Docker: " + CheckOS.isRunningInsideDocker());
        if (CheckOS.isUnix()) {
            logger.info("Trying to get Linux Distro name: " + CheckOS.getLinuxName());
        }
        if (!(new File(getDataFolder() + "/html/").exists())) {
            sethtmlfiles();
        }
        if (!(new File(getDataFolder() + "/php/").exists())){
            setphpfiles();
        }
        if (new File("plugins/WebPlugin/ssl/").exists()){
            logger.info("SSL Folder exist!");
        }else {
            logger.info("SSL Folder doesn't exist!");
            logger.info("Making!");
            if (!new File(getDataFolder() + "/SSL/", "removeme.txt").exists()) {
                saveResource("ssl/removeme.txt", false);
                File removeme = new File("ssl/removeme.txt");
                removeme.delete();
            }
        } //configuration.getString
        if (new File("plugins/WebPlugin/ssl/" + configuration.getString("SSLSettings.SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        if (CheckOS.isWindows()) {
            logger.severe("Currently i cannot make php work on windows in a proxy server.");
        }
        if (configuration.getString("Settings.HTMLPORT") != null){
            try {
                listeningport = (int) configuration.getInt("Settings.HTMLPORT");
                ss = new ServerSocket(listeningport);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            if (configuration.getString("Settings.HTMLPORT") == null){
                logger.severe("Port not found! Using internal default port!");
                try {
                    listeningport = 25567;
                    ss = new ServerSocket(listeningport);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (CheckOS.isWindows()){
            Startwebserverhtml();
            //Startwebserverphp();
            //I'm currently unable to make PHP work because I can't access libraries the same way as on SpigotMC.
        }
        if (CheckOS.isUnix()){
            Startwebserverhtml();
            //StartwebserverphpLinux();
            //I'm currently unable to make PHP work because I can't access libraries the same way as on SpigotMC.
            if (!CheckOS.isRunningInsideDocker()) {
                logger.info("You are currently running " + this.getDescription().getName() + " in a linux machine but not in a docker container!");
            }
        }
    }

    @Override
    public void onDisable() {

    }

    public void makeConfig() throws IOException {
        // Create plugin config folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getLogger().info("Created config folder: " + getDataFolder().mkdir());
        }

        File configFile = new File(getDataFolder(), "config.yml");
        File mimeFile = new File(getDataFolder(), "mime_types.yml");
        // Copy default config if it doesn't exist
        if (!configFile.exists()) {
            FileOutputStream outputStream = new FileOutputStream(configFile); // Throws IOException
            InputStream in = getResourceAsStream("config.yml"); // This file must exist in the jar resources folder
            in.transferTo(outputStream); // Throws IOException
        }
        if (!mimeFile.exists()){
            FileOutputStream outputStream = new FileOutputStream(mimeFile); // Throws IOException
            InputStream in = getResourceAsStream("mime_types.yml"); // This file must exist in the jar resources folder
            in.transferTo(outputStream);

        }
    }
    private void setphpfiles(){
        saveResource("php/index.php", false);
    }
    private void sethtmlfiles(){
        saveResource("html/index.html", false);
    }
    private void saveResource(String resourcePath, boolean replace) {
        try {
            Path targetFile = getDataFolder().toPath().resolve(resourcePath);
            if (Files.exists(targetFile) && !replace) {
                logger.info("Resource " + resourcePath + " already exists and replace is set to false. Skipping copy.");
                return;
            }

            // Ensure parent directories exist
            Files.createDirectories(targetFile.getParent());

            // Copy the resource from the JAR to the target location
            try (InputStream in = getClass().getResourceAsStream("/" + resourcePath)) {
                if (in == null) {
                    logger.severe("Resource " + resourcePath + " not found in plugin JAR.");
                    return;
                }
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Resource " + resourcePath + " copied to " + targetFile);
            }
        } catch (Exception e) {
            logger.severe("Failed to save resource " + resourcePath);
        }
    }
    private void Startwebserverhtml(){
        acceptorRunning = true;
        boolean enableHTML = (Boolean) configuration.getBoolean("Settings.EnableHTML");
        boolean enableSSL = (Boolean) configuration.getBoolean("Settings.EnableSSL");
        if (enableHTML){
            if (enableSSL){
                ProxyServer.getInstance().getScheduler().schedule(this, () -> {
//                    new SSLHtml().run();
                }, 1l, TimeUnit.MILLISECONDS);
            }else{
                ProxyServer.getInstance().getScheduler().schedule(this, () -> {
                    try {
                        logger.info("Starting accepting none SSL HTML connections!");
                        while (acceptorRunning) {
                            try {
                                Socket socket = ss.accept();
                                new NoneSSLHtmlProxyBun(socket, m).start();
                            } catch (IOException e) {
                                logger.severe("Error accepting socket connection");
                            }
                        }
                    } catch (Exception e) {
                        logger.severe("Error in acceptor task");
                    }
                }, 1l, TimeUnit.MILLISECONDS);
            }
        }
    }
}
