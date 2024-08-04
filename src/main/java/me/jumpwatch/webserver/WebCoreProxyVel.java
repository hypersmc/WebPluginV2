package me.jumpwatch.webserver;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.jumpwatch.webserver.html.NoneSSLHtmlProxyVel;
import me.jumpwatch.webserver.php.linux.PhpInstaller;
import me.jumpwatch.webserver.php.linux.PHPWebServerVel;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import org.bstats.velocity.Metrics;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 25-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
@Plugin(id= "webplugin", name = "webplugin", version = "2.5R", authors = "JumpWatch, HypersMC, HumpJump")
public class WebCoreProxyVel  {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebCoreProxyVel.class);
    public String pluginversion = "2.5R";
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private int portphp;
    private WebCoreProxyVel m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    private ServerSocket ssphp;
    public static String ver;
    private int version = 11;
    public ContentTypeResolver resolver;
    private ScheduledTask scheduledTask;
    private ScheduledTask scheduledTaskSSL;
    private ScheduledTask acceptorTask;
    private final Metrics.Factory metricsFactory;


    public static File dataFolder;
    private final ProxyServer proxyServer;
    private Logger logger;
    public static Map<String, Object> config;
    public static Map<String, Object> mime;
    public static Map<String, Object> settings;
    public static Map<String, Object> sslSettings;
    public static Map<String, Object> linuxPhpSettings;
    public boolean debug;
    @Inject
    public WebCoreProxyVel(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder, Metrics.Factory metricsFactory) {
        this.proxyServer = proxy;
        this.logger = Logger.getLogger("WebPluginProxyVel");
        this.metricsFactory = metricsFactory;
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
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
        logger.info("WebPluginProxy initializing");
        checkAndUpdateConfig();
        int pluginid = 22871;
        Metrics metrics = metricsFactory.make(this, pluginid);
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
        debug = (Boolean) settings.get("debug");
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
        if (settings.get("PHPPort") != null){
            try {
                portphp = (int) settings.get("PHPPort");
                ssphp = new ServerSocket(portphp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            if (settings.get("PHPPort") == null){
                logger.severe("Port not found! Using internal default port!");
                try {
                    portphp = (int) 25568;
                    ssphp = new ServerSocket(portphp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
            //I'm currently unable to make PHP work because I can't access libraries the same way as on SpigotMC. An alternative fix is under way :)
            //It's not prioritized as most hosts are using linux docker containers
        }
        if (CheckOS.isUnix()){
            Startwebserverhtml();
            StartwebserverphpLinux();
            if (!CheckOS.isRunningInsideDocker()) {
                logger.info("You are currently running " + this.getName() + " in a linux machine but not in a docker container!");
                logger.info("It's recommended to run minecraft servers in docker containers.");
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
    private void StartwebserverphpLinux() {
        boolean enablePHP = (boolean) settings.get("EnablePHP");
        if (enablePHP) {
            if (!new File("plugins/webplugin/phplinux/bin/php8/bin/php").exists()) {
                PhpInstaller.installphp();
                PhpInstaller.FilePermissions(); //Extra check as you never know :)
                if (new File("plugins/webplugin/phplinux/bin/php8/bin/php").exists()) {
                    startwebserverphp();
                }
            } else {
                startwebserverphp();
            }
        }
    }
    private void startwebserverphp() {
        acceptorRunning = true;
        acceptorTask = proxyServer.getScheduler().buildTask(this, () -> {
            while (acceptorRunning) {
                try {
                    Socket sock = ssphp.accept();
                    new PHPWebServerVel(sock, m).start();
                } catch (IOException e) {
                    logger.severe("Error accepting socket connection");
                    if (debug) e.printStackTrace();
                }
            }
        }).schedule(); // Adjust delay as necessary
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
                                if (debug) e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        logger.severe("Error in acceptor task");
                    }
                }).schedule(); // Adjust delay as necessary
            }
        }
    }
    private void checkAndUpdateConfig() {
        File configFile = new File(dataFolder.toPath().toFile(), "config.yml");
        File backupFile = new File(dataFolder.toPath().toFile(), "config_backup.yml");
        logger.info("Checking config version!");
        if (!configFile.exists()) {
            logger.warning("No config version found. Either config corrupt or never existed.");
            // If config doesn't exist, copy the default config
            copyDefaultConfig(configFile);
        } else {
            // Config exists, check version
            int configVersion = getConfigVersion(configFile);
            if (configVersion == -99 || configVersion != version) {
                logger.warning("Config is not right. Config is missing an update or you changed it!");
                logger.info("An backup will be made!");
                logger.info("Making backup!");
                // Backup old config and replace with default
                backupOldConfig(configFile, backupFile);
                logger.info("Done!");
                logger.info("Config was not up to date!");
                logger.info("Recreating!");
                copyDefaultConfig(configFile);
            }else{
                logger.info("Config is up to date!");
            }
        }
    }
    private int getConfigVersion(File configFile) {
        try {
            Properties properties = new Properties();
            properties.load(Files.newInputStream(configFile.toPath()));
            return Integer.parseInt(properties.getProperty("ConfigVersion"));
        } catch (IOException e) {
            logger.severe("Error reading config version");
            return -99;
        }
    }

    private void backupOldConfig(File configFile, File backupFile) {
        try {
            if (configFile.exists()) {
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up old config to " + backupFile.getPath());
            }
        } catch (IOException e) {
            logger.severe("Error backing up old config");
        }
    }

    private void copyDefaultConfig(File configFile) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (inputStream == null) {
                logger.severe("Default config file not found in JAR.");
                return;
            }
            Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Copied default config to " + configFile.getPath());
        } catch (IOException e) {
            logger.severe("Error copying default config");
        }
    }
}
/*
  I hate doing proxy support.
 */