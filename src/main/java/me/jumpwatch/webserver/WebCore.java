package me.jumpwatch.webserver;

import me.jumpwatch.webserver.html.NoneSSLHtml;
import me.jumpwatch.webserver.html.SSLHtml;
import me.jumpwatch.webserver.php.linux.LinuxPHPNginxCore;
import me.jumpwatch.webserver.php.linux.installers.LinuxInstaller;
import me.jumpwatch.webserver.php.windows.WindowsPHPNginxCore;
import me.jumpwatch.webserver.php.windows.installers.WinInstaller;
import me.jumpwatch.webserver.utils.AutoJKS;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.CommandManager;
import me.jumpwatch.webserver.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.FileUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
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
    private int version = 6;
    private CommandManager commandManager;
    private synchronized boolean getAcceptorRunning() {
        return acceptorRunning;
    }
    public void sethtmlfiles(){
        saveResource("html/index.html", false);
        saveResource("html/assets/css/font-awesome.min.css", false);
        saveResource("html/assets/css/main.css", false);
        saveResource("html/assets/fonts/FontAwesome.otf", false);
        saveResource("html/assets/fonts/fontawesome-webfont.eot", false);
        saveResource("html/assets/fonts/fontawesome-webfont.svg", false);
        saveResource("html/assets/fonts/fontawesome-webfont.ttf", false);
        saveResource("html/assets/fonts/fontawesome-webfont.woff", false);
        saveResource("html/assets/fonts/fontawesome-webfont.woff2", false);
        saveResource("html/assets/js/jquery.min.js", false);
        saveResource("html/assets/js/jquery.poptrox.min.js", false);
        saveResource("html/assets/js/main.js", false);
        saveResource("html/assets/js/skel.min.js", false);
        saveResource("html/images/bg.jpg", false);
    }

    @Override
    public void onEnable() {
        this.shutdown = false;
        this.getLogger().info("Current OS: " + CheckOS.OS);
        this.getLogger().info("Is running Docker: " + CheckOS.isRunningInsideDocker());
        configcontrol();
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        Logger logger = this.getLogger();
        if (this.getConfig().getBoolean("Settings.Autokey")){
            try {
                AutoJKS.makeJKS();
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableEntryException e) {
                e.printStackTrace();
            }
        }
        new UpdateChecker(this, 85640).getVersion(version -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                logger.info("There is no new update available.");
            }else{
                logger.info("There is a new update available!");
                logger.info("Your version is " + this.getDescription().getVersion() + " newest version " + version);
            }
            ver = version;
        });
        commandManager = new CommandManager(this, "WebP");
        registerCommand();
        if (!(new File(getDataFolder() + "/html/").exists())) {
            sethtmlfiles();
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

        if (new File("plugins/WebPlugin/ssl/" + getConfig().getString("SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }

        if (CheckOS.isWindows()) {
            if (new File("plugins/WebPlugin/phpwindows").exists() && new File("plugins/WebPlugin/phpwindows/php").exists() && new File("plugins/WebPlugin/phpwindows/nginx").exists()) {
                logger.info("Core Windows PHP files exist!");
            } else {
                logger.info("Starting to download Files for Nginx and PHP for Windows");
                try {
                    WinInstaller.WindowsPHPNginxInstaller();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
        if (CheckOS.isUnix()) {
            if (new File("plugins/WebPlugin/phplinux").exists() && new File("plugins/WebPlugin/phplinux/php").exists() && new File("plugins/WebPlugin/phplinux/nginx").exists()) {
                logger.info("Core Linux PHP files exist!");
            } else {
                logger.info("Starting to download files for Nginx and PHP for Linux");
                try {
                    LinuxInstaller.LinuxPHPNginxInstaller();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
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
                    listeningport = getConfig().getInt("Settings.HTMLPORT");
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
        if (CheckOS.isWindows()) {
            Startwebserver();
        }
        if (CheckOS.isUnix()) {
            if (!CheckOS.isRunningInsideDocker()) {
                logger.info("You are currently running " + this.getName() + " in a linux machine but not in a docker container!");
                logger.info("For reasons you will NEED to run the server in a docker container so it can install everything.");
            }else{
                //Startwebserver();
            }
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            ss.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (getConfig().getBoolean("Settings.EnablePHP")) {
            if (CheckOS.isWindows()) {
                WindowsPHPNginxCore.StopWindowsNginxandPHP();
            }else if (CheckOS.isUnix()) {
                //LinuxPHPNginxCore
            }
        }
    }

    private void Startwebserver(){
        acceptorRunning = true;
        if (getConfig().getBoolean("Settings.EnableHTML")) {
            if (getConfig().getBoolean("SSLSettings.EnableSSL")) {
                new BukkitRunnable() {
                    @Override
                    public void run(){
                        new SSLHtml().run();
                    }
                }.runTaskAsynchronously(this);
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
                                e.printStackTrace();
                            }
                        }
                    }
                });
                acceptor.start();
            }
        }
        if (getConfig().getBoolean("Settings.EnablePHP")) {
            new BukkitRunnable() {
                @Override
                public void run(){
                    WindowsPHPNginxCore.StartWindowsNginxandPHP();
                }
            }.runTaskAsynchronously(this);

        }
    }

    private void registerCommand(){
        commandManager.register("help", ((sender, params) -> {
            if (sender.hasPermission("web.help") || sender.hasPermission("web.*")) {
                sender.sendMessage(prefix + " Commands: ");
                sender.sendMessage(prefix + " /webp reload reloads the plugin's configuration (NOT RESET)");
                sender.sendMessage(prefix + " /webp dev gets who developed this plugin and plugin version");
                sender.sendMessage(prefix + " /webp ver gets plugin version and checks if there is a new version");
                sender.sendMessage(prefix + " /webp help to get this again.");
                sender.sendMessage(prefix + " /webp stopweb (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp startweb (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp reloadweb (ONLY works if php is enabled!");
                sender.sendMessage(prefix + " /webp reset (ONLY works in console!)");
            } else {
                sender.sendMessage(prefix + " It appears you do not have the right permissions to do this!");
            }
        }));

        commandManager.register("stopweb", ((sender, params) -> {
            if (sender.hasPermission("web.stop") || sender.hasPermission("web.*")) {
                if (CheckOS.isWindows()) {
                    WindowsPHPNginxCore.StopWindowsNginxandPHP();
                    sender.sendMessage(prefix + " Stopped webserver (PHP)");
                }
                if (CheckOS.isUnix()) {
                    if (CheckOS.isRunningInsideDocker()){
                        sender.sendMessage(prefix + " Docker container system for PHP is in the works.");
                    }else {
                        sender.sendMessage(prefix + " Plain none dockered linux will not get PHP.");

                    }
                }
            }
        }));

        commandManager.register("startweb", ((sender, params) -> {
            if (sender.hasPermission("web.start") || sender.hasPermission("web.*")) {
                if (CheckOS.isWindows()) {
                    WindowsPHPNginxCore.StartWindowsNginxandPHP();
                    sender.sendMessage(prefix + " Started webserver (PHP)");
                }
                if (CheckOS.isUnix()) {
                    if (CheckOS.isRunningInsideDocker()){
                        sender.sendMessage(prefix + " Docker container system for PHP is in the works.");
                    }else {
                        sender.sendMessage(prefix + " Plain none dockered linux will not get PHP.");

                    }
                }
            }
        }));

        commandManager.register("reloadweb", ((sender, params) -> {
            if (sender.hasPermission("web.reload") || sender.hasPermission("web.*")) {
                if (CheckOS.isWindows()) {
                    WindowsPHPNginxCore.reloadWindowsNginxandPHP();
                    sender.sendMessage(prefix + " Reloaded webserver (PHP)");
                }
                if (CheckOS.isUnix()) {
                    if (CheckOS.isRunningInsideDocker()){
                        sender.sendMessage(prefix + " Docker container system for PHP is in the works.");
                    }else {
                        sender.sendMessage(prefix + " Plain none dockered linux will not get PHP.");

                    }
                }
            }
        }));

        commandManager.register("reload", ((sender, params) -> {
            if (sender.hasPermission("web.reload") || sender.hasPermission("web.*")) {
                reloadConfig();
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

        commandManager.register("", ((sender, params) -> {
            if (!(sender instanceof Player)){
                if (getConfig().getString("Settings.ServerIP").equalsIgnoreCase("localhost")){
                    sender.sendMessage(prefix + ChatColor.RED + " ServerIP not changed in config!");
                    sender.sendMessage(prefix + " Webserver info: ");
                    if (getConfig().getBoolean("SSLSettings.EnableSSL")) {
                        sender.sendMessage("");
                    }
                }else {
                    sender.sendMessage(prefix + " Webserver info: ");
                    if (getConfig().getBoolean("SSLSettings.EnableSSL")){

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

    public static void FilesPermissionsCheckLinux(WebCore core){
        File phpin1 = new File(core.getDataFolder() + "/phplinux/php/php-8.0.10/./build");
        File phpin2 = new File(core.getDataFolder() + "/phplinux/php/php-8.0.10/./build/shtool");
        String phpin3 = "sudo chmod a+x " + core.getDataFolder() + "/phplinux/php/php-8.0.10/";
        String perms1 = "sudo chmod a+x " + core.getDataFolder() + "/phplinux/php/php-8.0.10/./build/shtool";
        String perms2 = "sudo chmod a+x " + core.getDataFolder() + "/phplinux/php/php-8.0.10/./build";
        String FULL = "sudo chown -R root:root " + core.getDataFolder() + "/phplinux/php/php-8.0.10/*";
        try {
            core.getLogger().info("Trying to set permissions for Linux");
            phpin1.setExecutable(true, false);
            phpin1.setReadable(true, false);
            phpin1.setWritable(true, false);
            phpin2.setExecutable(true, false);
            phpin2.setReadable(true, false);
            phpin2.setWritable(true, false);
            Process pro = Runtime.getRuntime().exec(phpin3);
            Process pro2 = Runtime.getRuntime().exec(perms1);
            Process pro3 = Runtime.getRuntime().exec(perms2);
            try {
                core.getLogger().info("Trying to set full permissions to ROOT");
                Process FULl = Runtime.getRuntime().exec(FULL);
            } catch (Exception e) {
                core.getLogger().info("Failure to give full permissions to ROOT");
                if (core.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
            }
            core.getLogger().info("File permission check success!");
        } catch (Exception e) {
            core.getLogger().info("Failed to check permissions! Please enable debug mode and report back to me (dev)");
            if (core.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

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

}
