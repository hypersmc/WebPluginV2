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
import me.jumpwatch.webserver.automation.WebPluginAutomation;
import me.jumpwatch.webserver.php.linux.PHPWebServerVel;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import org.bstats.velocity.Metrics;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.google.inject.Inject;
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
@Plugin(id= "webplugin", name = "webplugin", version = "4.2B", authors = "JumpWatch, HypersMC, HumpJump")
public class WebCoreProxyVel  {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(WebCoreProxyVel.class);
    public String pluginversion = "4.2B";
    public static String closeConnection = "!Close Connection!";
    public WebPluginAutomation automation;
    private int listeningport;
    private int portphp;
    private WebCoreProxyVel m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 18;
    public ContentTypeResolver resolver;
    private ScheduledTask scheduledTask;
    private ScheduledTask scheduledTaskSSL;
    private ScheduledTask acceptorTask;
    private final Metrics.Factory metricsFactory;


    public static File dataFolder;
    public final ProxyServer proxyServer;
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
        if (automation != null) { automation.close(); automation = null; }
        boolean enableSSL = (Boolean) sslSettings.get("EnableSSL");
        boolean enableHTML = (Boolean) settings.get("EnableHTML");
        boolean enablePHP = (Boolean) settings.get("EnablePHP");
//        scheduledTask.cancel();
        if (scheduledTaskSSL != null) scheduledTaskSSL.cancel();
        if (acceptorTask != null) acceptorTask.cancel();
        if (enablePHP) new PHPWebServerVel(this).stopsystem();
        this.shutdown = true;
        acceptorRunning = false;
        if (ss != null) {
            try { ss.close(); } catch (IOException e) {
                logger.warning("Could not close HTML listener: " + e.getMessage());
            } finally { ss = null; }
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
        if (new File("plugins/webplugin/ssl/").exists()){
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
        if (new File("plugins/webplugin/ssl/" + sslSettings.get("SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        if (CheckOS.isWindows()) {
            logger.severe("Windows is not supported!");
        }
        // Do not bind HTMLPORT unless the standalone non-SSL HTML listener is actually enabled.
        if (asBoolean(settings.get("EnableHTML"), false) && !asBoolean(sslSettings.get("EnableSSL"), false)) {
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
        }
        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("webptasks").plugin(this).build(),
                new com.velocitypowered.api.command.SimpleCommand() {
                    @Override public void execute(Invocation invocation) {
                        if (!invocation.source().hasPermission("webplugin.tasks")) {
                            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("[WebPlugin] No permission."));
                            return;
                        }
                        if (automation == null) {
                            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("[WebPlugin] Automation not initialized."));
                            return;
                        }
                        for (String line : automation.command(java.util.Arrays.asList(invocation.arguments())))
                            invocation.source().sendMessage(net.kyori.adventure.text.Component.text("[WebPlugin] " + line));
                    }
                });
        boolean automationReady = initializeAutomation();
        if (CheckOS.isWindows()){
            Startwebserverhtml();
        }
        if (CheckOS.isUnix()){
            Startwebserverhtml();
            if (automationReady) {
                StartwebserverphpLinux();
                if (automation != null && asBoolean(settings.get("EnablePHP"), false)) automation.startTasksIfReady();
            }
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
    private Object automationSetting(String path, Object fallback) {
        Object value = config;
        for (String part : path.split("\\.")) {
            if (!(value instanceof java.util.Map<?, ?>)) return fallback;
            value = ((java.util.Map<?, ?>) value).get(part);
        }
        return value == null ? fallback : value;
    }

    private boolean initializeAutomation() {
        if (!CheckOS.isUnix()) return true;
        try {
            Object raw = automationSetting("DeploymentTasks.Tasks", java.util.List.of());
            java.util.List<java.util.Map<?, ?>> taskConfigs = new java.util.ArrayList<>();
            if (raw instanceof java.util.List<?>) {
                for (Object entry : (java.util.List<?>) raw)
                    if (entry instanceof java.util.Map<?, ?>)
                        taskConfigs.add((java.util.Map<?, ?>) entry);
            }
            Object cap = automationSetting("DeploymentTasks.MaxProcesses", 8);
            automation = new WebPluginAutomation(
                    new PHPWebServerVel(this).runtimeOptions(),
                    asString(automationSetting("Settings.IndexLocation", "")),
                    asBoolean(automationSetting("Security.Landlock.Enabled", false), false),
                    asString(automationSetting("Security.Landlock.Mode", "preferred")),
                    asString(automationSetting("Security.Landlock.HelperPath", "")),
                    asBoolean(automationSetting("DeploymentTasks.Enabled", false), false),
                    cap instanceof Number ? ((Number) cap).intValue() : 8,
                    taskConfigs, message -> logger.info("[Automation] " + message));
            return true;
        } catch (Exception ex) {
            logger.severe("Automation startup failed; PHP will not launch without sandbox policy: " + ex.getMessage());
            return false;
        }
    }

    private void StartwebserverphpLinux() {
        boolean enablePHP = (boolean) settings.get("EnablePHP");
        if (enablePHP) {
            File phpBinary = new File(dataFolder, "phplinux/bin/php8/bin/php");
            if (!phpBinary.exists()) {
                boolean installed = PhpInstaller.installphp(
                        dataFolder,
                        asString(config.get("GithubWebPluginPHPRepo")),
                        asString(config.get("GithubWebPluginPHPRepoFilename")),
                        asString(config.get("GithubWebPluginNGINXRepoFilename")),
                        debugEnabled()
                );
                if (installed && phpBinary.exists()) {
                    new PHPWebServerVel(this).start();
                }
            } else {
                PhpInstaller.FilePermissions(dataFolder);
                new PHPWebServerVel(this).start();
            }
        }
    }

    private boolean debugEnabled() {
        return asBoolean(WebCoreProxyVel.settings.get("debug"), false);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private void Startwebserverhtml(){
        boolean enableHTML = asBoolean(settings.get("EnableHTML"), false);
        if (!enableHTML) {
            acceptorRunning = false;
            logger.info("Standalone HTML web server is disabled (Settings.EnableHTML=false).");
            return;
        }
        if (!asBoolean(sslSettings.get("EnableSSL"), false) && ss == null) {
            logger.severe("Standalone HTML listener cannot start because HTMLPORT was not bound.");
            return;
        }
        acceptorRunning = true;
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
                                if (acceptorRunning) logger.severe("HTML accept failed: " + e.getMessage());
                                if (acceptorRunning && debug) e.printStackTrace();
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
