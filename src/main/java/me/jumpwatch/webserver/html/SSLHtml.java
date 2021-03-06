package me.jumpwatch.webserver.html;

import me.jumpwatch.webserver.WebCore;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.*;

public class SSLHtml {
    WebCore main = JavaPlugin.getPlugin(WebCore.class);
    static String DEFAULT_FAIL = "index.html";


    public void run(){
        try {
            var address = new InetSocketAddress("localhost", main.getConfig().getInt("SSLSettings.SSLPort"));
            startSingleThreaded(address);
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

        }
    }

    public void startSingleThreaded(InetSocketAddress address) {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        System.out.println("Start single-threaded server at " + address);
        BufferedOutputStream dataOut = null;



        try (var serverSocket = getServerSocket(address)) {

            BufferedReader in = null;
            String fileRequested = null;

            while (true) {
                try (var socket = serverSocket.accept();
                     var reader = new BufferedReader(new InputStreamReader(
                             socket.getInputStream()));

                     var writer = new BufferedWriter(new OutputStreamWriter(
                             socket.getOutputStream()))
                ) {


                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    String input = in.readLine();
                    StringTokenizer parse = new StringTokenizer(input);
                    String method = parse.nextToken().toUpperCase();
                    fileRequested = parse.nextToken().toLowerCase();
                    String s;
                    int counter = 0, contentLength = 0;
                    try {
                        while (!(s = in.readLine()).equals("")){
                            if (counter == 0 && s.equalsIgnoreCase(WebCore.closeConnection)) {

                                in.close();
                                writer.close();
                                dataOut.close();

                                return;
                            }
                            if (s.startsWith("Content-Length: ")) {
                                contentLength = Integer.parseInt(s.split("Length: ")[1]);
                            }
                            counter++;
                        }
                    }catch (IOException e) {
                        main.getServer().getLogger().info("This is not an error and should not be reported.");
                        main.getServer().getLogger().info("Counting failed!");
                    }
                    String finalString = "";
                    for (int i = 0; i < contentLength; i++) {
                        finalString += (char) reader.read();
                    }
                    if (fileRequested.endsWith("/")) {
                        fileRequested += DEFAULT_FAIL;
                    }
                    File file = new File(main.getDataFolder() + "/html/", fileRequested);
                    int fileLength = (int) file.length();
                    String content = getContentType(fileRequested);

                    dataOut = new BufferedOutputStream(socket.getOutputStream());
                    if (method.equals("GET")) {
                        try {
                            byte[] fileData = readFileData(file, fileLength);

                            writer.write("HTTP/1.1 200 OK\r\n");
                            writer.write("Server: Java HTTP Server from SSaurel : 1.0\r\n");
                            writer.write("Set-Cookie: Max-Age=0; Secure;\r\n");
                            writer.write("Date: " + new Date() + "\r\n");
                            writer.write("Content-type: " + content + "\r\n");
                            writer.write("\r\n");
                            writer.flush();
                            dataOut.write(fileData, 0, fileLength);
                            dataOut.flush();
                        } catch (IOException e) {
                            main.getServer().getLogger().info("This is not an error and should not be reported.");
                            main.getServer().getLogger().info("Writing failed!");
                        }
                    }else if (method.equals("POST")) {
                        try {
                            byte[] fileData = readFileData(file, fileLength);

                            writer.write("HTTP/1.1 200 OK\r\n");
                            writer.write("Server: Java HTTP Server from SSaurel : 1.0\r\n");
                            writer.write("Set-Cookie: Max-Age=0; Secure;\r\n");
                            writer.write("Date: " + new Date() + "\r\n");
                            writer.write("Content-type: " + content + "\r\n");
                            writer.write("\r\n");
                            writer.flush();
                            dataOut.write(fileData, 0, fileLength);
                            dataOut.flush();
                        } catch (IOException e) {
                            main.getServer().getLogger().info("This is not an error and should not be reported.");
                            main.getServer().getLogger().info("Writing failed!");
                        }
                    }
                    writer.close();
                    socket.close();
                    dataOut.close();

                } catch (IOException e) {
                    System.err.println("Exception while handling connection");
                    if (e.getMessage().contains("Received fatal alert: certificate_unknown")){
                        System.err.println("Received fatal alert: certificate_unknown");
                    }
                    if (e.getMessage().contains("An established connection was aborted by the software in your host machine")){
                        System.err.println("An established connection was aborted by the software of client machine");
                    }
                    if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

                }

            }
        } catch (Exception e) {
            System.err.println("Could not create socket at " + address);
            main.getLogger().info("Restarting SSL systems!");
            this.run();

            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

        }
    }
    private static byte[] readFileData(File file, int fileLength) throws IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
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
    private static String getContentType(String fileRequested) {
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
    private static ServerSocket getServerSocket(InetSocketAddress address)
            throws Exception {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String ksName = "plugins/WebPlugin/ssl/" + main.getConfig().getString("SSLSettings.SSLJKSName").toString() + ".jks";
        char ksPass[] = main.getConfig().getString("SSLSettings.SSLJKSPass").toString().toCharArray();
        char ctPass[] = main.getConfig().getString("SSLSettings.SSLJKSKey").toString().toCharArray();

        int backlog = 0;

        var serverSocket = getSslContext(Path.of(ksName), ksPass)
                .getServerSocketFactory()
                .createServerSocket(address.getPort(), backlog, address.getAddress());

        Arrays.fill(ksPass, '0');

        return serverSocket;
    }

    private static SSLContext getSslContext(Path keyStorePath, char[] keyStorePass)
            throws Exception {

        var keyStore = KeyStore.getInstance("JKS");
        keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePass);

        var keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, keyStorePass);

        var sslContext = SSLContext.getInstance("TLSv1.2");
        // Null means using default implementations for TrustManager and SecureRandom
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }



    private static List<String> getHeaderLines(BufferedReader reader)
            throws IOException {
        var lines = new ArrayList<String>();
        var line = reader.readLine();
        // An empty line marks the end of the request's header
        while (!line.isEmpty()) {
            lines.add(line);
            line = reader.readLine();
        }
        return lines;
    }
}