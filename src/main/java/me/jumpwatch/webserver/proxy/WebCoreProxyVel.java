package me.jumpwatch.webserver.proxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.jumpwatch.webserver.html.NoneSSLHtmlProxyVel;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 25-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
@Plugin(id= "webplugin", name = "webplugin", version = "2.4R", authors = "JumpWatch, HypersMC, HumpJump")
public class WebCoreProxyVel  {
    public String pluginversion = "2.4R";
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private WebCoreProxyVel m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 10;
    public ContentTypeResolver resolver;
    private ScheduledTask scheduledTask;
    private ScheduledTask scheduledTaskSSL;
    private ScheduledTask acceptorTask;


    public static File dataFolder;
    private final ProxyServer proxyServer;
    private Logger logger;
    public static Map<String, Object> config;
    public static Map<String, Object> mime;
    public static Map<String, Object> settings;
    public static Map<String, Object> sslSettings;
    public static Map<String, Object> linuxPhpSettings;
    @Inject
    public WebCoreProxyVel(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxyServer = proxy;
        this.logger = Logger.getLogger("WebPluginProxyVel");
        WebCoreProxyVel.dataFolder = dataFolder.toFile();
    }
    private void setphpfiles(){
        saveResource("php/index.php", false);
    }
    private void sethtmlfiles(){
        saveResource("html/index.html", false);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("WebCoreProxyVel is shutting down.");
        boolean enableSSL = (Boolean) sslSettings.get("EnableSSL");
        boolean enableHTML = (Boolean) settings.get("EnableHTML");
//        scheduledTask.cancel();
        if (enableSSL) scheduledTaskSSL.cancel();
        if (enableHTML) acceptorTask.cancel();
        this.shutdown = true;
        acceptorRunning = false;
        Socket sockCloser;
        try {
            sockCloser = new Socket("localhost", listeningport);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sockCloser.getOutputStream()));
            out.write(this.closeConnection);
            out.close();
            sockCloser.close();
            getLogger().info( "Closed listening web server successfully!");
        }catch (IOException e) {
            e.printStackTrace();
        }
        try {
            ss.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //No php yet

    }

    @Subscribe //Velocity
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("WebPluginProxy initialized");
        loadConfig();
        loadMime();
        resolver = new ContentTypeResolver();
        resolver.loadContentTypesVel();
        //noinspection unchecked
        settings = (Map<String, Object>) config.get("Settings");
        //noinspection unchecked
        sslSettings = (Map<String, Object>) config.get("SSLSettings");
        //noinspection unchecked
        linuxPhpSettings = (Map<String, Object>) config.get("Linuxphpsettings");
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
        }
        if (new File("plugins/WebPlugin/ssl/" + sslSettings.get("SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        if (CheckOS.isWindows()) {
            logger.severe("Currently i cannot make php work on windows in a proxy server.");
        }
        if (settings.get("HTMLPORT") != null){
            try {
                listeningport = (int) settings.get("HTMLPORT");
                ss = new ServerSocket(listeningport);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            if (settings.get("HTMLPORT") == null){
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
                logger.info("You are currently running " + this.getName() + " in a linux machine but not in a docker container!");
            }
        }


//        Map<String, Object> settings = (Map<String, Object>) config.get("Settings");
//        Map<String, Object> sslSettings = (Map<String, Object>) config.get("SSLSettings");
//        Map<String, Object> linuxPhpSettings = (Map<String, Object>) config.get("Linuxphpsettings");
//
//        String serverIP = (String) settings.get("ServerIP");
//        int htmlPort = (Integer) settings.get("HTMLPORT");
//        boolean debug = (Boolean) settings.get("debug");
//        int configversion = (Integer) config.get("ConfigVersion");
//
//        logger.info("Server IP: " + serverIP);
//        logger.info("HTML Port: " + htmlPort);
//        logger.info("Debug mode: " + debug);
//        logger.info("Config Version: " + configversion);

    }
    public String getName() {
        return getClass().getAnnotation(Plugin.class).name();
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public Logger getLogger() {
        return logger;
    }



    private void loadConfig() {
        try {
            Path configFile = dataFolder.toPath().resolve("config.yml");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in == null) {
                        logger.severe("Resource config.yml not found in plugin JAR.");
                        return;
                    }
                    Files.createDirectories(dataFolder.toPath());
                    Files.copy(in, configFile);
                    logger.info("Default configuration file copied to: " + configFile);
                }
            }
            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configFile)) {
                config = yaml.load(in);
            }
        } catch (Exception e) {
            logger.severe("Failed to load configuration file");
        }
    }
    private void loadMime() {
        try {
            Path configFile = dataFolder.toPath().resolve("mime_types.yml");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/mime_types.yml")) {
                    if (in == null) {
                        logger.severe("Resource mime_types.yml not found in plugin JAR.");
                        return;
                    }
                    Files.createDirectories(dataFolder.toPath());
                    Files.copy(in, configFile);
                    logger.info("Default Mime file copied to: " + configFile);
                }
            }
            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configFile)) {
                mime = yaml.load(in);
            }
        } catch (Exception e) {
            logger.severe("Failed to load Mime file");
        }
    }

    private void saveConfig() {
        try {
            Path configFile = dataFolder.toPath().resolve("config.yml");
            Yaml yaml = new Yaml();
            try (OutputStream out = Files.newOutputStream(configFile);
                 OutputStreamWriter writer = new OutputStreamWriter(out)) {
                yaml.dump(config, writer);
            }
        } catch (Exception e) {
            logger.severe("Failed to save configuration file");
        }
    }
    private void saveResource(String resourcePath, boolean replace) {
        try {
            Path targetFile = dataFolder.toPath().resolve(resourcePath);
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
        boolean enableHTML = (Boolean) settings.get("EnableHTML");
        boolean enableSSL = (Boolean) sslSettings.get("EnableSSL");
        if (enableHTML){
            if (enableSSL){
                scheduledTaskSSL = proxyServer.getScheduler().buildTask(this, () -> {
//                    new SSLHtml().run();
                }).repeat(10, TimeUnit.SECONDS).schedule();
                logger.info("Async task for SSL started with ID: " + scheduledTaskSSL.status());
            }else{
                acceptorTask = proxyServer.getScheduler().buildTask(this, () -> {
                    try {
                        logger.info("Starting accepting none SSL HTML connections!");
                        while (acceptorRunning) {
                            try {
                                Socket sock = ss.accept();
                                new NoneSSLHtmlProxyVel(sock, m).start();
                            } catch (IOException e) {
                                logger.severe("Error accepting socket connection");
                            }
                        }
                    } catch (Exception e) {
                        logger.severe("Error in acceptor task");
                    }
                }).schedule(); // Adjust delay as necessary
            }
        }
    }
}
/*
  I hate doing proxy support.
 */