package me.jumpwatch.webserver.html;

import me.jumpwatch.webserver.proxy.WebCoreProxyVel;
import me.jumpwatch.webserver.utils.ContentTypeResolver;

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * @author JumpWatch on 26-07-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class NoneSSLHtmlProxyVel extends Thread{
    Socket socket;
    WebCoreProxyVel main;
    String DEFAULT_FILE = "index.html";
    public NoneSSLHtmlProxyVel(Socket socket, WebCoreProxyVel main) {
        this.socket = socket;
        this.main = main;
    }

    @Override
    public void run() {
        try(
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            BufferedOutputStream dataOut = new BufferedOutputStream(socket.getOutputStream())
        ) {
            processRequest(in, out, dataOut);
        } catch (Exception e){
            boolean debug = (Boolean) WebCoreProxyVel.settings.get("debug");
            if (debug) e.printStackTrace();
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
                if (in.readLine() == null) return;
                while (!(s = in.readLine()).equals("")) {
                    if (counter == 0 && s.equalsIgnoreCase(WebCoreProxyVel.closeConnection)) {
                        closeSocket();
                        return;
                    }
                    if (s.startsWith("Content-Length: ")) {
                        contentLength = Integer.parseInt(s.split("Length: ")[1]);
                    }
                    counter++;
                }
            }catch (IOException e) {
                main.getLogger().info("This is not an error and should not be reported.");
                main.getLogger().info("Counting failed!");
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
            resolver.loadContentTypesVel();
            String content = resolver.getContentType(fileRequested);
            // send HTTP Headers
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Server: Java HTTP Server from WebPlugin : " + main.pluginversion + "\r\n");
            out.println("Set-Cookie: Max-Age=0; HttpOnly");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content + "\r\n");
            out.flush(); // flush character output stream buffer

            byte[] fileData = readFileData(file, fileLength);

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } catch (IOException e) {
            main.getLogger().info("Error processing request: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }
    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            main.getLogger().severe("Error closing socket: " + e.getMessage());
        }
    }
    private byte[] readFileData(File file, int fileLength) throws IOException {
        byte[] fileData = new byte[fileLength];
        try (FileInputStream fileIn = new FileInputStream(file)) {
            fileIn.read(fileData);
        } catch (IOException e) {
            main.getLogger().severe("Error reading file data: " + e.getMessage());
        }
        return fileData;
    }
}
