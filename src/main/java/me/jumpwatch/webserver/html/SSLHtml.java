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
package me.jumpwatch.webserver.html;

import me.jumpwatch.webserver.WebCore;
import me.jumpwatch.webserver.utils.ContentTypeResolver;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.*;

public class SSLHtml {
    WebCore main = JavaPlugin.getPlugin(WebCore.class);
    static String DEFAULT_FAIL = "index.html";
    private static int st = 1;
    private static int fp = 5;


    public void run(){
        try {
            var address = new InetSocketAddress("0.0.0.0", main.getConfig().getInt("SSLSettings.SSLPort"));
            startSingleThreaded(address);
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();

        }
    }

    public void startSingleThreaded(InetSocketAddress address) {
        System.out.println("Start single-threaded server at " + address);
        BufferedOutputStream dataOut = null;

        try (var serverSocket = getServerSocket(address)) {

            BufferedReader in = null;
            String fileRequested = null;

            while (true) {
                try (var socket = serverSocket.accept();
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    char[] buffer = new char[1024];
                    int bytesRead = in.read(buffer, 0, buffer.length);

                    if (bytesRead == -1) {
                        System.out.println("No data received. Client may have closed the connection.");
                        // Handle the situation accordingly, e.g., break out of the loop.
                        break;
                    }

                    // Convert chars to String
                    String input = new String(buffer, 0, bytesRead);
                    StringTokenizer parse = new StringTokenizer(input);
                    String method = parse.nextToken().toUpperCase();
                    fileRequested = parse.nextToken().toLowerCase();
                    String s;
                    int counter = 0, contentLength = 0;

                    try {
                        while (!(s = in.readLine()).equals("")) {
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
                    } catch (IOException e) {
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
                    ContentTypeResolver resolver = new ContentTypeResolver();
                    String content = resolver.getContentType(fileRequested);

                    dataOut = new BufferedOutputStream(socket.getOutputStream());
                    if (method.equals("GET")) {
                        try {
                            byte[] fileData = readFileData(file, fileLength);

                            writer.write("HTTP/1.1 200 OK\r\n");
                            writer.write("Server: Java HTTPS Server from WebPlugin : " + main.getDescription().getVersion() + "\r\n");
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
                    } else if (method.equals("POST")) {
                        try {
                            byte[] fileData = readFileData(file, fileLength);

                            writer.write("HTTP/1.1 200 OK\r\n");
                            writer.write("Server: Java HTTPS Server from WebPlugin : " + main.getDescription().getVersion() + "\r\n");
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
                    if (e.getMessage().contains("Received fatal alert: certificate_unknown")) {
                        System.err.println("Received fatal alert: certificate_unknown");
                    }
                    if (e.getMessage().contains("An established connection was aborted by the software in your host machine")) {
                        System.err.println("An established connection was aborted by the software of the client machine");
                    }
                    if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
                }

            }
        } catch (Exception e) {
            if (st < fp) {
                System.err.println("Could not create socket at " + address);
                main.getLogger().info("Restarting SSL systems!");
                st = st + 1;
                this.run();
                if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
            } else {
                main.getLogger().info("Max restarts exceeded. Please enable debug and view error");
                if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
                return;
            }
        }
    }

    private static byte[] readFileData(File file, int fileLength) throws IOException {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];

        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } catch (IOException e) {
            main.getServer().getLogger().info("This is not an error and should not be reported.");
            main.getServer().getLogger().info("File: " + file + " Could not be found!");
        } finally {
            if (fileIn != null)
                fileIn.close();
        }

        return fileData;
    }
    private static ServerSocket getServerSocket(InetSocketAddress address) throws Exception {
        WebCore main = JavaPlugin.getPlugin(WebCore.class);
        String certFile = main.getDataFolder() + "/ssl/" + main.getConfig().getString("SSLSettings.SSLPubl");
        String keyFile = main.getDataFolder() + "/ssl/" + main.getConfig().getString("SSLSettings.SSLPriv");

        SSLContext sslContext = getSslContext(certFile, keyFile);

        int backlog = 0;

        return sslContext.getServerSocketFactory().createServerSocket(address.getPort(), backlog, address.getAddress());
    }

    private static SSLContext getSslContext(String certFile, String keyFile) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (FileInputStream certInputStream = new FileInputStream(certFile)) {
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(certInputStream);
            int i = 0;
            for (Certificate certificate : certificates) {
                keyStore.setCertificateEntry("cert" + i, certificate);
                i++;
            }
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        try (FileInputStream keyInputStream = new FileInputStream(keyFile)) {
            keyManagerFactory.init(keyStore, keyInputStream == null ? null : keyInputStream.toString().toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                new SecureRandom()
        );

        return sslContext;
    }
}