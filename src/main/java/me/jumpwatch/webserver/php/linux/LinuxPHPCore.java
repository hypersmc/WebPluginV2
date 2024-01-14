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
package me.jumpwatch.webserver.php.linux;

import com.caucho.quercus.servlet.QuercusServlet;
import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class LinuxPHPCore {
    private static Server server;

    public static void StartLinuxPHP() {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        if (main.getConfig().getBoolean("Settings.EnablePHP")) {
            server = new Server(Integer.parseInt(main.getConfig().getString("Settings.PHPPort")));
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setResourceBase(main.getDataFolder() + "/php/");
            context.setContextPath("/");
            context.addServlet(DefaultServlet.class, "/");
            context.setWelcomeFiles(new String[]{"index.php"});

//            ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
//            errorHandler.addErrorPage(500, 599, main.getConfig().getString("Linuxphpsettings.Errorfolder"));
//            context.setErrorHandler(errorHandler);


            server.setHandler(context);
            ServletHolder phpServletHolder = new ServletHolder(new QuercusServlet());
            phpServletHolder.setInitOrder(1);

            context.addServlet(phpServletHolder, "*.php");
            try {
                server.start();
                server.join();
            } catch (Exception e) {
                if (main.getConfig().getBoolean("Settings.debug")) {
                    main.getLogger().info(e.toString());
                }
            }
        }
    }
    public static void StopLinuxPHP() {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        if (main.getConfig().getBoolean("Settings.EnablePHP")) {
            try {
                server.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    public static void restartLinuxPHP(){
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        if (main.getConfig().getBoolean("Settings.EnablePHP")) {
            try {
                StopLinuxPHP();
                StartLinuxPHP();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
