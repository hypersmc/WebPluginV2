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

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;
public class NoneSSLHtml extends Thread{
    Socket sock;
    WebCore main;
    String DEFAULT_FILE = "index.html";
    public NoneSSLHtml(Socket sock, WebCore main) {
        this.sock = sock;
        this.main = main;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                PrintWriter out = new PrintWriter(sock.getOutputStream());
                BufferedOutputStream dataOut = new BufferedOutputStream(sock.getOutputStream())
        ) {
            processRequest(in, out, dataOut);
        } catch (Exception e) {
            if (main.getConfig().getBoolean("Settings.debug")) e.printStackTrace();
        }
    }
    private void processRequest(BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) {
        String fileRequested;
        try {
            String input = in.readLine();
            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();
            fileRequested = parse.nextToken().toLowerCase();
            String s;
            int counter = 0, contentLength = 0;
            try {
                while (!(s = in.readLine()).equals("")) {
                    if (counter == 0 && s.equalsIgnoreCase(WebCore.closeConnection)) {
                        closeSocket();
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
                finalString += (char) in.read();
            }

            if (fileRequested.endsWith("/")) {
                fileRequested += DEFAULT_FILE;
            }
            File file = new File(main.getDataFolder() + "/html/", fileRequested);
            int fileLength = (int) file.length();
            ContentTypeResolver resolver = new ContentTypeResolver();
            String content = resolver.getContentType(fileRequested);

            // send HTTP Headers
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Server: Java HTTP Server from WebPlugin : " + main.getDescription().getVersion() + "\r\n");
            out.println("Set-Cookie: Max-Age=0; HttpOnly");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content + "\r\n");
            out.flush(); // flush character output stream buffer

            byte[] fileData = readFileData(file, fileLength);

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();

        } catch (IOException e) {
            main.getServer().getLogger().info("Error processing request: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }
    private void closeSocket() {
        try {
            sock.close();
        } catch (IOException e) {
            main.getServer().getLogger().info("Error closing socket: " + e.getMessage());
        }
    }
    private byte[] readFileData(File file, int fileLength) throws IOException {
        byte[] fileData = new byte[fileLength];
        try (FileInputStream fileIn = new FileInputStream(file)) {
            fileIn.read(fileData);
        } catch (IOException e) {
            main.getServer().getLogger().info("Error reading file data: " + e.getMessage());
        }
        return fileData;
    }
}
