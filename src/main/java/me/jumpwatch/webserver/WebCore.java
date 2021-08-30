package me.jumpwatch.webserver;

import me.jumpwatch.webserver.html.NoneSSLHtml;
import me.jumpwatch.webserver.html.SSLHtml;
import me.jumpwatch.webserver.php.windows.WindowsPHPNginxCore;
import me.jumpwatch.webserver.php.windows.installers.WinInstaller;
import me.jumpwatch.webserver.utils.CheckOS;
import me.jumpwatch.webserver.utils.CommandManager;
import me.jumpwatch.webserver.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
import java.util.logging.Logger;

public class WebCore extends JavaPlugin {
    private boolean debug;
    public static String closeConnection = "!Close Connection!";
    private int listeningport;
    private WebCore m = this;
    private boolean shutdown = false;
    private Thread acceptor;
    private boolean acceptorRunning;
    private ServerSocket ss;
    public static String ver;
    private int version = 5;
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
            this.getLogger().warning("Config is not right. Config was missing an update or you changed it!");
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
        getConfig().options().copyDefaults();
        saveDefaultConfig();
        Logger logger = this.getLogger();
        new UpdateChecker(this, 85640).getVersion(version -> {
            if (this.getDescription().getVersion().equalsIgnoreCase(version)) {
                logger.info("There is no new update available.");
                ver = version;
            }else{
                logger.info("There is a new update available!");
                logger.info("Your version is " + this.getDescription().getVersion() + " newest version " + version);
                ver = version;
            }
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
            }
        }

        if (new File("plugins/WebPlugin/ssl/" + getConfig().getString("SSLJKSName") + ".jks").exists()){
            logger.info("SSL File exist!");
        } else {
            logger.info("SSL File doesn't exist!");
        }
        if (new File("plugins/WebPlugin/phpwindows").exists() && new File("plugins/WebPlugin/phpwindows/php").exists() && new File("plugins/WebPlugin/phpwindows/nginx").exists()) {
            logger.info("Core PHP files exist!");
        } else {
            logger.info("Starting to download Files for Nginx and PHP");
            WinInstaller.WindowsPHPNginxInstaller();
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
            logger.info("Due to some issues PHP on linux is currently not a thing.");
        }
    }

    @Override
    public void onDisable() {
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
            WindowsPHPNginxCore.StopWindowsNginxandPHP();
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
        commandManager.register("", ((sender, params) -> {

        }));
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
