/*
 * ******************************************************
 *  *Copyright (c) 2020-2022. Jesper Henriksen mhypers@gmail.com
 *
 *  * This file is part of WebServer project
 *  *
 *  * WebServer can not be copied and/or distributed without the express
 *  * permission of Jesper Henriksen
 *  ******************************************************
 */
package me.jumpwatch.webserver;

import me.jumpwatch.webserver.html.NoneSSLHtml;
import me.jumpwatch.webserver.html.SSLHtml;
import me.jumpwatch.webserver.php.linux.PHPWebServer;
import me.jumpwatch.webserver.php.linux.PhpInstaller;
import me.jumpwatch.webserver.automation.WebPluginAutomation;
import me.jumpwatch.webserver.utils.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.FileUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class WebCore extends JavaPlugin {
    private boolean debug = this.getConfig().getBoolean("Settings.debug");
    public static String prefix = ChatColor.translateAlternateColorCodes('&', "&7[&3&lWebPlugin&7]&r");
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private WebCore m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 18;
    private CommandManager commandManager;
    public WebPluginAutomation automation;
    public ContentTypeResolver resolver;
    final util util = new util();

    private synchronized boolean getAcceptorRunning() {
        return acceptorRunning;
    }
    private void setphpfiles(){
        saveResource("php/index.php", false);
    }
    private void sethtmlfiles(){
        saveResource("html/index.html", false);
    }

    @Override
    public void onEnable() {
        int pluginid = 22869;
        Metrics metrics = new Metrics(this, pluginid);
        resolver = new ContentTypeResolver();
        resolver.loadContentTypesBukkit();
        if (!new File(getDataFolder(), "mime_types.yml").exists()) saveResource("mime_types.yml", false);
        this.shutdown = false;
        this.getLogger().info("Current OS: " + CheckOS.OS);
        this.getLogger().info("Is running Docker: " + CheckOS.isRunningInsideDocker());
        if (CheckOS.isUnix()){
            this.getLogger().info("Trying to get Linux Distro name: " + CheckOS.getLinuxName());
        }
        configcontrol();
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        Logger logger = this.getLogger();
        if (this.getConfig().getBoolean("Settings.Autokey")){
            try {
//                LetsEncryptCertificateMaker.generateLetsEncryptCertificate(getConfig().getString("SSLSettings.SSLDomain"));
                logger.info("AutoPEM  is currently disabled there was issues with it!");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (this.getDescription().getVersion().equalsIgnoreCase("devbuild")){
            logger.info("This is a dev build! Please remind me on Discord that I uploaded a dev build :)");
        }else {
            new UpdateChecker(this, 85640).getVersion(version -> {
                if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                    logger.info("There is no new update available.");
                } else {
                    logger.info("There is a new update available!");
                    logger.info("Your version is " + this.getDescription().getVersion() + " newest version " + version);
                }
                ver = version;
            });
        }
        commandManager = new CommandManager(this, "WebP");
        registerCommand();
        if (getConfig().getBoolean("Settings.PHPFolderPermissionFix")){
            dofolderfix();
            getConfig().set("Settings.PHPFolderPermissionFix", false);
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

        if (new File("plugins/webplugin/ssl/" + getConfig().getString("SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        // The Java HTML listener is independent of PHP/NGINX and uses HTMLPORT only without SSL.
        if (getConfig().getBoolean("Settings.EnableHTML") && !getConfig().getBoolean("SSLSettings.EnableSSL")) {
        if (getConfig().isSet("Settings.HTMLPORT")) {
            try {
                listeningport = getConfig().getInt("Settings.HTMLPORT");
                ss = new ServerSocket(listeningport);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            if (getConfig().contains("Settings.HTMLPORT")) {
                getLogger().warning("Port not found! Using internal default port!");
                try {
                    listeningport = 25567;
                    ss = new ServerSocket(listeningport);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                getLogger().warning("Plugin disabled! NO VALUE WAS FOUND FOR LISTENING PORT!");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }
        }
        boolean automationReady = initializeAutomation();
        if (CheckOS.isWindows()) {
            Startwebserverhtml();
        }
        if (CheckOS.isUnix()) {
            Startwebserverhtml();
            if (automationReady) {
                StartwebserverphpLinux();
                if (automation != null && getConfig().getBoolean("Settings.EnablePHP")) automation.startTasksIfReady();
            }
            if (!CheckOS.isRunningInsideDocker()) {
                logger.info("You are currently running " + this.getName() + " in a linux machine but not in a docker container!");
            }
        }
        getServer().getConsoleSender().sendMessage(
                "\n \n" + ChatColor.DARK_GRAY + "[]=====["
                        + ChatColor.GRAY +"Enabling "+ getDescription().getName()  + ChatColor.RESET
                        + ChatColor.DARK_GRAY + "]=====[]" + ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY + "| " + ChatColor.RESET
                        + ChatColor.RED + "Logged info:"  + ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY +"|   " + ChatColor.RESET
                        + ChatColor.RED +"Name: " + ChatColor.RESET
                        + ChatColor.GRAY + getDescription().getName()  + ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY +"|   " + ChatColor.RESET
                        + ChatColor.RED +"Developer: " + ChatColor.RESET
                        + ChatColor.GRAY + getDescription().getAuthors().toString().replace("[", "").replace("]", "") + ChatColor.RESET +"\n"
                        + ChatColor.DARK_GRAY +"|   " + ChatColor.RESET
                        + ChatColor.RED +"Version: " + ChatColor.RESET
                        + ChatColor.GRAY +"v-" + getDescription().getVersion() + ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY +"|   " + ChatColor.RESET
                        + ChatColor.RED +"Soft Dependencies: " + ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY +"|      " + ChatColor.GREEN +"We are Soft Dependencies free!!"+ ChatColor.RESET + "\n"
                        + ChatColor.DARK_GRAY +"|   " + ChatColor.RESET
                        + ChatColor.RED +"Features enabled: " + ChatColor.RESET + "\n"
                        + util.detectSettingWebsite(this)
                        + ChatColor.DARK_GRAY + "[]=====["
                        + ChatColor.GRAY +"Enabling "+ getDescription().getName()  + ChatColor.RESET
                        + ChatColor.DARK_GRAY + "]=====[]" + ChatColor.RESET + "\n\n");

    }

    private void dofolderfix() {
        try {
            Process userProcess = Runtime.getRuntime().exec("whoami");
            BufferedReader reader = new BufferedReader(new InputStreamReader(userProcess.getInputStream()));
            String currentUser = reader.readLine(); // Get the output (username)
            reader.close();
            String command = "chown -R " + currentUser + ": /home/container/plugins/webplugin/php";
            Process chownProcess = Runtime.getRuntime().exec(command);
            chownProcess.waitFor(); // Wait for the process to complete

            WPLogger.info("Command executed successfully for user " + currentUser);

        } catch (IOException | InterruptedException e) {
            try {
                String command = "chown -R www-data: /home/container/plugins/webplugin/php";
                Process chownProcess = Runtime.getRuntime().exec(command);
                chownProcess.waitFor(); // Wait for the process to complete

                WPLogger.info("Command executed successfully for www-data");
            } catch (Exception ex) {
                if (debug) DebugLogger.error(ex.getMessage());
            }
            if (debug) DebugLogger.error(e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (automation != null) { automation.close(); automation = null; }
        if (!isFolia()) {
            Bukkit.getScheduler().cancelTasks(m); //problem in folia
            Bukkit.getScheduler().cancelTasks(this); //problem in folia
        }
        this.shutdown = true;
        acceptorRunning = false;
        if (ss != null) {
            try { ss.close(); } catch (IOException e) {
                getLogger().warning("Could not close HTML listener: " + e.getMessage());
            } finally { ss = null; }
        }
        if (getConfig().getBoolean("Settings.EnablePHP")) {
            if (CheckOS.isUnix()) {

            }
        }

    }

    /** Initialize the optional security manager before any PHP/NGINX processes are launched. */
    private boolean initializeAutomation() {
        if (!CheckOS.isUnix()) return true;
        try {
            automation = new WebPluginAutomation(
                    new PHPWebServer(this).runtimeOptions(),
                    getConfig().getString("Settings.IndexLocation", ""),
                    getConfig().getBoolean("Security.Landlock.Enabled", false),
                    getConfig().getString("Security.Landlock.Mode", "preferred"),
                    getConfig().getString("Security.Landlock.HelperPath", ""),
                    getConfig().getBoolean("DeploymentTasks.Enabled", false),
                    getConfig().getInt("DeploymentTasks.MaxProcesses", 8),
                    getConfig().getMapList("DeploymentTasks.Tasks"),
                    message -> getLogger().info("[Automation] " + message));
            return true;
        } catch (Exception ex) {
            getLogger().severe("Automation startup failed; PHP will not launch without the configured sandbox policy: " + ex.getMessage());
            return false;
        }
    }

    private void StartwebserverphpLinux(){
        if (getConfig().getBoolean("Settings.EnablePHP")){
            File phpBinary = new File(getDataFolder(), "phplinux/bin/php8/bin/php");
            if (!phpBinary.exists()) {
                boolean installed = PhpInstaller.installphp(
                        getDataFolder(),
                        getConfig().getString("GithubWebPluginPHPRepo"),
                        getConfig().getString("GithubWebPluginPHPRepoFilename"),
                        getConfig().getString("GithubWebPluginNGINXRepoFilename"),
                        getConfig().getBoolean("Settings.debug")
                );
                if (installed && phpBinary.exists()) {
                    new PHPWebServer(this).start();
                }
            } else {
                PhpInstaller.FilePermissions(getDataFolder());
                new PHPWebServer(this).start();
            }
        }
    }
    private void Startwebserverhtml(){
        if (!getConfig().getBoolean("Settings.EnableHTML")) {
            acceptorRunning = false;
            getLogger().info("Standalone HTML web server is disabled (Settings.EnableHTML=false).");
            return;
        }
        if (!getConfig().getBoolean("SSLSettings.EnableSSL") && ss == null) {
            getLogger().severe("Standalone HTML listener cannot start because HTMLPORT was not bound.");
            return;
        }
        acceptorRunning = true;
        if (getConfig().getBoolean("Settings.EnableHTML")) {
            if (getConfig().getBoolean("SSLSettings.EnableSSL")) {
                new BukkitRunnable() {
                    @Override
                    public void run(){
                        new SSLHtml().run();
                    }
                }.runTaskAsynchronously(this).getTaskId() ;
            }else{
                acceptor = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Socket sock;
                        getServer().getLogger().info("Starting accepting none SSL HTML connections!");
                        while (getAcceptorRunning()) {
                            try {
                                sock = ss.accept();
                                new NoneSSLHtml(sock, m).start();
                            } catch (IOException e) {
                                if (getAcceptorRunning()) getLogger().warning("HTML accept failed: " + e.getMessage());
                            }
                        }
                    }
                });
                acceptor.start();
            }
        }

    }

    private void registerCommand(){
        commandManager.register("tasks", ((sender, params) -> {
            if (!sender.hasPermission("web.tasks") && !sender.hasPermission("web.*") && !sender.isOp()) {
                sender.sendMessage(prefix + " You do not have permission to manage deployment tasks.");
                return;
            }
            if (automation == null) {
                sender.sendMessage(prefix + " Automation is not initialized.");
                return;
            }
            for (String line : automation.command(java.util.Arrays.asList(params)))
                sender.sendMessage(prefix + " " + line);
        }));
        commandManager.register("sandbox", ((sender, params) -> {
            if (!sender.hasPermission("web.tasks") && !sender.hasPermission("web.*") && !sender.isOp()) {
                sender.sendMessage(prefix + " You do not have permission to inspect the sandbox.");
                return;
            }
            if (automation == null) {
                sender.sendMessage(prefix + " Sandbox not initialized.");
                return;
            }
            java.util.List<String> arguments = new java.util.ArrayList<>();
            arguments.add("sandbox");
            arguments.addAll(java.util.Arrays.asList(params));
            for (String line : automation.command(arguments))
                sender.sendMessage(prefix + " " + line);
        }));
        commandManager.register("help", ((sender, params) -> {
            if (sender.hasPermission("web.help") || sender.hasPermission("web.*")) {
                sender.sendMessage(prefix + " Commands: ");
                sender.sendMessage(prefix + " /webp reload | reloads the plugin's configuration (NOT RESET)");
                sender.sendMessage(prefix + " /webp dev | gets who developed this plugin and plugin version");
                sender.sendMessage(prefix + " /webp ver | gets plugin version and checks if there is a new version");
                sender.sendMessage(prefix + " /webp help | to get this again.");
                sender.sendMessage(prefix + " /webp tasks list|status|start|stop|logs | Manage configured PHP workers.");
                sender.sendMessage(prefix + " /webp sandbox test | Check filesystem isolation without creating a public PHP endpoint.");
                sender.sendMessage(prefix + " /webp sandbox result | Show latest filesystem self-test.");
                sender.sendMessage(prefix + " /webp stopweb | (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp startweb | (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp webreload | (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp reset | (ONLY works in console!)");
                sender.sendMessage(prefix + " /webp uploadlogs <debug:true|false> [includeConfig:true|false] [redactConfig:true|false] | Upload logs for debugging purposes");
            } else {
                sender.sendMessage(prefix + " It appears you do not have the right permissions to do this!");
            }
        }));

        commandManager.register("stopweb", ((sender, params) -> {
            if (sender.hasPermission("web.stop") || sender.hasPermission("web.*")) {
                if (getConfig().getBoolean("Settings.EnablePHP")) {
                    if (CheckOS.isUnix()) {
                        sender.sendMessage(prefix + " Stopping PHP webserver on linux is no longer possible.");
                    }
                }else {
                    sender.sendMessage(prefix + " PHP is not enabled!.");
                }
            }
        }));

        commandManager.register("startweb", ((sender, params) -> {
            if (sender.hasPermission("web.start") || sender.hasPermission("web.*")) {
                if (getConfig().getBoolean("Settings.EnablePHP")) {
                    if (CheckOS.isUnix()) {
                        sender.sendMessage(prefix + " Linux php webserver will automatically start itself.");
                    }
                }else {
                    sender.sendMessage(prefix + " PHP is not enabled!.");
                }
            }
        }));

        commandManager.register("webreload", ((sender, params) -> {
            if (sender.hasPermission("web.webreload") || sender.hasPermission("web.*")) {
                if (getConfig().getBoolean("Settings.EnablePHP")) {
                    if (CheckOS.isUnix()) {
                        sender.sendMessage(prefix + " Reload not possible anymore on Linux webserver (PHP)");
                    }
                }else {
                    sender.sendMessage(prefix + " PHP is not enabled!.");
                }
            }
        }));

        commandManager.register("reload", ((sender, params) -> {
            if (sender.hasPermission("web.reload") || sender.hasPermission("web.*")) {
                reloadConfig();
                resolver.loadContentTypesBukkit();
                sender.sendMessage(prefix + " Configuration file reloaded.");
            }else {
                sender.sendMessage(prefix + " It appears you do not have the right permissions to do this!");
            }
        }));

        commandManager.register("dev", ((sender, params) -> {
            if (sender.hasPermission("web.dev") || sender.hasPermission("web.*")) {
                sender.sendMessage(prefix + " This plugin is developed by " + getDescription().getAuthors());
                sender.sendMessage(prefix + " Your running version: " + ChatColor.RED + getDescription().getVersion());
            }else {
                sender.sendMessage(prefix + " It appears you do not have the right permissions to do this!");
            }
        }));

        commandManager.register("ver", ((sender, params) -> {
            if (sender.hasPermission("web.ver") || sender.hasPermission("web.*")) {
                sender.sendMessage(prefix + " Your running version: " + ChatColor.RED + getDescription().getVersion() + ChatColor.RESET + getver());
            }else {
                sender.sendMessage(prefix + " It appears you do not have the right permissions to do this!");
            }
        }));

        commandManager.register("reset", ((sender, params) -> {
            if (CommandValidate.console(sender)) return;
            if (params.length == 0){
                sender.sendMessage(prefix + ChatColor.RED + " [WARNING] THIS WILL ERASE EVERYTHING IN THE CONFIG!");
                sender.sendMessage(prefix + " Please confirm the reset!");
                sender.sendMessage(prefix + " /webp reset confirm");
                return;
            }
            if (params.length == 1) {
                sender.sendMessage(prefix + " Starting config reset!");
                resetconfig(((Player) sender).getPlayer());
            }
        }));

        commandManager.register("uploadlogs", ((sender, params) -> {
            if (sender.hasPermission("web.logsupload") || sender.hasPermission("web.*")) {
                if (params.length >= 1) {
                    boolean useDebug = Boolean.parseBoolean(params[0]);
                    boolean includeConfig = params.length >= 2 && Boolean.parseBoolean(params[1]);
                    boolean redactConfig = params.length >= 3 && Boolean.parseBoolean(params[2]);

                    String url = LogUploader.uploadLatestLog(useDebug, includeConfig, redactConfig);
                    if (url == null) {
                        sender.sendMessage(prefix + ChatColor.RED + " [WARNING] No log files found!");
                    } else {
                        sender.sendMessage(prefix + " Uploaded log: " + url + "/raw");
                    }
                } else {
                    sender.sendMessage(prefix + " /webp uploadlogs <debug:true|false> [includeConfig:true|false] [redactConfig:true|false]");
                }
            } else {
                sender.sendMessage(prefix + " You do not have permission to do this!");
            }
        }));

        commandManager.register("", ((sender, params) -> {
            if (!(sender instanceof Player)){
                if (getConfig().getBoolean("SSLSettings.EnableSSL")) {
                    boolean enablePHP = getConfig().getBoolean("Settings.EnablePHP");
                    boolean enableHTML = getConfig().getBoolean("Settings.EnableHTML");
                    String serverIP = getConfig().getString("Settings.ServerIP");

                    if (enablePHP && enableHTML) {
                        // Both PHP and HTML are enabled
                        sender.sendMessage(prefix + " URL HTML: https://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enablePHP) {
                        // Only PHP is enabled
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enableHTML) {
                        // Only HTML is enabled
                        sender.sendMessage(prefix + " URL HTML: https://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                    } else {
                        // Neither PHP nor HTML is enabled
                        sender.sendMessage(prefix + " Both PHP and HTML are disabled.");
                    }
                }else{

                    boolean enablePHP = getConfig().getBoolean("Settings.EnablePHP");
                    boolean enableHTML = getConfig().getBoolean("Settings.EnableHTML");
                    String serverIP = getConfig().getString("Settings.ServerIP");

                    if (enablePHP && enableHTML) {
                        // Both PHP and HTML are enabled
                        sender.sendMessage(prefix + " URL HTML: http://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enablePHP) {
                        // Only PHP is enabled
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enableHTML) {
                        // Only HTML is enabled
                        sender.sendMessage(prefix + " URL HTML: http://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                    } else {
                        // Neither PHP nor HTML is enabled
                        sender.sendMessage(prefix + " Both PHP and HTML are disabled.");
                    }
                }
            }else{
                if (getConfig().getBoolean("SSLSettings.EnableSSL")) {
                    boolean enablePHP = getConfig().getBoolean("Settings.EnablePHP");
                    boolean enableHTML = getConfig().getBoolean("Settings.EnableHTML");
                    String serverIP = getConfig().getString("Settings.ServerIP");

                    if (enablePHP && enableHTML) {
                        // Both PHP and HTML are enabled
                        sender.sendMessage(prefix + " URL HTML: https://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enablePHP) {
                        // Only PHP is enabled
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enableHTML) {
                        // Only HTML is enabled
                        sender.sendMessage(prefix + " URL HTML: https://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                    } else {
                        // Neither PHP nor HTML is enabled
                        sender.sendMessage(prefix + " Both PHP and HTML are disabled.");
                    }
                }else{

                    boolean enablePHP = getConfig().getBoolean("Settings.EnablePHP");
                    boolean enableHTML = getConfig().getBoolean("Settings.EnableHTML");
                    String serverIP = getConfig().getString("Settings.ServerIP");

                    if (enablePHP && enableHTML) {
                        // Both PHP and HTML are enabled
                        sender.sendMessage(prefix + " URL HTML: http://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enablePHP) {
                        // Only PHP is enabled
                        sender.sendMessage(prefix + " URL PHP: http://" + serverIP + ":" + getConfig().getString("Settings.PHPPort"));
                    } else if (enableHTML) {
                        // Only HTML is enabled
                        sender.sendMessage(prefix + " URL HTML: http://" + serverIP + ":" + getConfig().getString("Settings.HTMLPORT"));
                    } else {
                        // Neither PHP nor HTML is enabled
                        sender.sendMessage(prefix + " Both PHP and HTML are disabled.");
                    }
                }
            }
        }));
    }

    public void configcontrol(){
        if (!(getConfig().contains("ConfigVersion", true))) {
            this.getLogger().warning("No config version found. Either config corrupt or never existed.");
            this.getLogger().info("In case on existed backup is being made.");
            File backup = new File(getDataFolder(), "config.yml");
            this.getLogger().info("Making backup");
            FileUtil.copy(backup, new File(backup + ".backup"));
            backup.delete();
            this.getLogger().info("Creating config from internal storage.");
            getConfig().options().copyDefaults();
            saveDefaultConfig();
        }else if ((getConfig().contains("ConfigVersion")) && (getConfig().getInt("ConfigVersion") != version)) {
            this.getLogger().warning("Config is not right. Config is missing an update or you changed it!");
            this.getLogger().info("An backup will be made.");
            File backup = new File(getDataFolder(), "config.yml");
            this.getLogger().info("Making backup");
            FileUtil.copy(backup, new File(backup + ".backup"));
            backup.delete();
            this.getLogger().info("Done!");
            this.getLogger().info("config was not up to date.");
            this.getLogger().info("RECREATING");
            saveResource("config.yml", true);
        }else {
            this.getLogger().info("Config up to date!");
        }
    }

    public void resetconfig(Player sender){
        sender.sendMessage("WARNING You are now resetting your config.yml");
        sender.sendMessage("An backup will be made!");
        File backup = new File(getDataFolder(), "config.yml");
        sender.sendMessage("Making backup");
        FileUtil.copy(backup, new File(backup + ".backup"));
        backup.delete();
        sender.sendMessage("Done!");
        sender.sendMessage("config was set to reset!.");
        sender.sendMessage("RECREATING");
        saveResource("config.yml", true);
    }
    public String getver(){
        if (getDescription().getVersion().equals(ver)) {
            return " and your running the newest version!";
        }else {
            return " and the newest version is: " + ChatColor.RED + ver;
        }
    }


    private static final class CommandValidate {
        private static boolean notPlayer(CommandSender sender) {
            if (!(sender instanceof Player))
                sender.sendMessage("This command can only be executed by a player.");
            return !(sender instanceof Player);
        }
        private static boolean console(CommandSender sender) {
            if (sender instanceof Player)
                sender.sendMessage("This command can only be executed in console.");
            return (sender instanceof Player);
        }
    }
    private boolean isFolia() {
        try {
            // Check if the RegionScheduler class exists (specific to Folia)
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false; // Not running on Folia
        }
    }
}
