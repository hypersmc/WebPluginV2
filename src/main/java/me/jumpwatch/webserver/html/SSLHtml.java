package me.jumpwatch.webserver.html;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.StringTokenizer;

public class SSLHtml {
    WebCore main = JavaPlugin.getPlugin(WebCore.class);
    String DEFAULT_FAIL = "index.html";

    public void run(){
        String ksName = "plugins/WebPlugin/ssl/" + main.getConfig().getString("SSLSettings.SSLJKSName").toString() + ".jks";
        char ksPass[] = main.getConfig().getString("SSLSettings.SSLJKSPass").toString().toCharArray();
        char ctPass[] = main.getConfig().getString("SSLSettings.SSLJKSKey").toString().toCharArray();
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;
        try {
            main.getServer().getLogger().info("Trying to start SSL systems!");
            if (!(new File("plugins/WebPlugin/ssl/" + main.getConfig().getString("SSLSettings.SSLJKSName") + ".jks").exists())) {
                main.getLogger().info("SSL key not found!");
                main.getLogger().info("Closing plugin to prevent unwanted damage!");
                Bukkit.getScheduler().cancelTasks(main);
                Bukkit.getPluginManager().disablePlugin(main);
            }
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(ksName), ksPass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, ctPass);
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);
            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket s = (SSLServerSocket) ssf.createServerSocket(main.getConfig().getInt("SSLSettings.SSLPort"));
            SSLSocket c = (SSLSocket) s.accept();
            main.getServer().getLogger().info("SSL Websocket established and a secure connection is made!");
            in = new BufferedReader(new InputStreamReader(c.getInputStream()));
            out = new PrintWriter(c.getOutputStream());
            dataOut = new BufferedOutputStream(c.getOutputStream());
            String input = in.readLine();
            StringTokenizer parse = new StringTokenizer(input);
            fileRequested = parse.nextToken().toLowerCase();
            String contentMimeType = "text/html";
            String s2;
            int counter = 0, contentLength = 0;
            try {
                while (!(s2 = in.readLine()).equalsIgnoreCase("")){
                    if (counter == 0 && s2.equalsIgnoreCase(WebCore.closeConnection)) {
                        out.close();
                        in.close();
                        s.close();
                        return;
                    }
                    if (s2.startsWith("Content-Length: ")) {
                        contentLength = Integer.parseInt(s2.split("Length: ")[1]);
                    }
                    counter++;
                }
            }catch (IOException e) {
                main.getServer().getLogger().info("This is not an error and should not be reported.");
                main.getServer().getLogger().info("Counting failed!");
            }
            String finalString = "";
            for (int i = 0; i < contentLength; i++) {
                finalString += (char) in.read();
            }
            if (fileRequested.endsWith("/")) {
                fileRequested += DEFAULT_FAIL;
            }
            File file = new File(main.getDataFolder() + "/html/", fileRequested);
            int fileLength = (int) file.length();
            String content = getContentType(fileRequested);

            try {
                byte[] fileData = readFileData(file, fileLength);
                out.write("HTTP/1.1 200 OK");
                out.write("Server: Java HTTP Server from SSaurel : 1.0");
                out.write("HTTP/1.1 200 OK");
                out.println("Content-type: " + content);
                out.println(); // blank line between headers and content, very important !
                out.flush(); // flush character output stream buffer
                dataOut.write(fileData, 0, fileLength);
                dataOut.flush();
            } catch (IOException e) {
                main.getServer().getLogger().info("This is not an error and should not be reported.");
                main.getServer().getLogger().info("Writing failed!");
            }
            out.close();
            in.close();
            c.close();
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];

        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } catch (IOException e){
            main.getServer().getLogger().info("This is not an error and should not be reported.");
            main.getServer().getLogger().info("File: " + file + " Could not be found!");
        }finally {
            if (fileIn != null)
                fileIn.close();
        }

        return fileData;
    }

    /*
    Returns supported MIME types
    Add a issue on github if more is needed!
     */
    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html")) {
            return "text/html";
        }else if (fileRequested.endsWith(".css")) {
            return "text/css";
        }else if (fileRequested.endsWith(".js")) {
            return "application/x-javascript";
        }else if (fileRequested.endsWith(".svg")){
            return "image/svg+xml";
        }else{
            return "text/plain";
        }
    }
}
