package me.jumpwatch.webserver;


import me.jumpwatch.webserver.html.NoneSSLHtmlProxyBun;
import me.jumpwatch.webserver.php.linux.PHPWebServerBun;
import me.jumpwatch.webserver.php.linux.PhpInstaller;
import me.jumpwatch.webserver.automation.WebPluginAutomation;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.bstats.bungeecord.Metrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author JumpWatch on 25-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class WebCoreProxy extends Plugin {
    public Configuration configuration;
    public WebPluginAutomation automation;
    public String pluginversion = "4.2B";
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private WebCoreProxy m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 18;
    public ContentTypeResolver resolver;
    private Logger logger = Logger.getLogger("WebPluginProxyBun");
    public static File dataFolder;
    @Override //All other (Bungeecord and such)
    public void onEnable() {
        int pluginid = 22870;
        Metrics metrics = new Metrics(this, pluginid);
        dataFolder = getDataFolder();
        getLogger().info("WebPluginProxy enabled");
        checkAndUpdateConfig();
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
        } //configuration.getString
        if (new File("plugins/webplugin/ssl/" + configuration.getString("SSLSettings.SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        if (CheckOS.isWindows()) {
            logger.severe("Windows is not supported!");
        }
        // Do not reserve an HTML port when the standalone HTML listener is disabled or using SSL.
        if (configuration.getBoolean("Settings.EnableHTML") && !configuration.getBoolean("SSLSettings.EnableSSL")) {
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
        }
        // Same shared task engine as Bukkit and Velocity; no arbitrary command execution.
        ProxyServer.getInstance().getPluginManager().registerCommand(this,
                new net.md_5.bungee.api.plugin.Command("webptasks", "webplugin.tasks") {
                    @Override
                    public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
                        if (!sender.hasPermission("webplugin.tasks")) {
                            sender.sendMessage(new net.md_5.bungee.api.chat.TextComponent("[WebPlugin] No permission."));
                            return;
                        }
                        if (automation == null) {
                            sender.sendMessage(new net.md_5.bungee.api.chat.TextComponent("[WebPlugin] Automation not initialized."));
                            return;
                        }
                        for (String line : automation.command(java.util.Arrays.asList(args)))
                            sender.sendMessage(new net.md_5.bungee.api.chat.TextComponent("[WebPlugin] " + line));
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
                if (automation != null && configuration.getBoolean("Settings.EnablePHP")) automation.startTasksIfReady();
            }
            if (!CheckOS.isRunningInsideDocker()) {
                logger.info("You are currently running " + this.getDescription().getName() + " in a linux machine but not in a docker container!");
            }
        }
    }

    @Override
    public void onDisable() {
        if (automation != null) { automation.close(); automation = null; }
        shutdown = true;
        acceptorRunning = false;
        if (ss != null) {
            try { ss.close(); } catch (IOException e) {
                logger.warning("Could not close HTML listener: " + e.getMessage());
            } finally { ss = null; }
        }
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
    private boolean initializeAutomation() {
        if (!CheckOS.isUnix() || configuration == null) return true;
        try {
            java.util.List<java.util.Map<?, ?>> taskConfigs = new java.util.ArrayList<>();
            Object raw = configuration.get("DeploymentTasks.Tasks");
            if (raw instanceof java.util.List<?>) {
                for (Object entry : (java.util.List<?>) raw) {
                    if (entry instanceof java.util.Map<?, ?>)
                        taskConfigs.add((java.util.Map<?, ?>) entry);
                    else if (entry instanceof Configuration) {
                        Configuration section = (Configuration) entry;
                        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
                        for (String key : section.getKeys()) values.put(key, section.get(key));
                        taskConfigs.add(values);
                    }
                }
            }
            automation = new WebPluginAutomation(
                    new PHPWebServerBun(this).runtimeOptions(),
                    configuration.getString("Settings.IndexLocation"),
                    configuration.getBoolean("Security.Landlock.Enabled"),
                    configuration.getString("Security.Landlock.Mode", "preferred"),
                    configuration.getString("Security.Landlock.HelperPath", ""),
                    configuration.getBoolean("DeploymentTasks.Enabled"),
                    configuration.getInt("DeploymentTasks.MaxProcesses", 8),
                    taskConfigs, message -> logger.info("[Automation] " + message));
            return true;
        } catch (Exception ex) {
            logger.severe("Automation startup failed; PHP will not launch without sandbox policy: " + ex.getMessage());
            return false;
        }
    }

    private void Startwebserverhtml(){
        boolean enableHTML = configuration.getBoolean("Settings.EnableHTML");
        if (!enableHTML) {
            acceptorRunning = false;
            logger.info("Standalone HTML web server is disabled (Settings.EnableHTML=false).");
            return;
        }
        if (!configuration.getBoolean("SSLSettings.EnableSSL") && ss == null) {
            logger.severe("Standalone HTML listener cannot start because HTMLPORT was not bound.");
            return;
        }
        acceptorRunning = true;
        boolean enableSSL = (Boolean) configuration.getBoolean("SSLSettings.EnableSSL");
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
                                if (acceptorRunning) logger.severe("Error accepting socket connection: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        logger.severe("Error in acceptor task");
                    }
                }, 1l, TimeUnit.MILLISECONDS);
            }
        }
    }
    private void StartwebserverphpLinux() {
        boolean enablePHP = (Boolean) configuration.getBoolean("Settings.EnablePHP");
        if (enablePHP) {
            File phpBinary = new File(getDataFolder(), "phplinux/bin/php8/bin/php");
            if (!phpBinary.exists()) {
                boolean installed = PhpInstaller.installphp(
                        getDataFolder(),
                        configuration.getString("GithubWebPluginPHPRepo"),
                        configuration.getString("GithubWebPluginPHPRepoFilename"),
                        configuration.getString("GithubWebPluginNGINXRepoFilename"),
                        configuration.getBoolean("Settings.debug")
                );
                if (installed && phpBinary.exists()) {
                    new PHPWebServerBun(this).start();
                }
            } else {
                PhpInstaller.FilePermissions(getDataFolder());
                new PHPWebServerBun(this).start();
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
