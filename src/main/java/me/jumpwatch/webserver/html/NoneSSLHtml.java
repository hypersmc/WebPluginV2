package me.jumpwatch.webserver.html;

import me.jumpwatch.webserver.WebCore;

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

        BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;
        String fileRequested = null;
        try {
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            out = new PrintWriter(sock.getOutputStream());
            dataOut = new BufferedOutputStream(sock.getOutputStream());
            String input = in.readLine();
            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();
            fileRequested = parse.nextToken().toLowerCase();
            String s;
            int counter = 0, contentLength = 0;
            try {
                while (!(s = in.readLine()).equals("")) {
                    if (counter == 0 && s.equalsIgnoreCase(WebCore.closeConnection)) {
                        out.close();
                        in.close();
                        sock.close();

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
            for(int i = 0; i < contentLength; i++){
                finalString += (char) in.read();
            }
            if (fileRequested.endsWith("/")) {
                fileRequested += DEFAULT_FILE;
            }
            File file = new File(main.getDataFolder() + "/html/", fileRequested);
            int fileLength = (int) file.length();
            String content = getContentType(fileRequested);
            if (method.equals("GET")) { // GET method so we return content
                try {

                    byte[] fileData = readFileData(file, fileLength);

                    // send HTTP Headers
                    out.write("HTTP/1.1 200 OK");
                    out.write("Server: Java HTTP Server from SSaurel : 1.0");
                    out.println("Set-Cookie: Max-Age=0; Secure; HttpOnly");
                    out.println("Date: " + new Date());
                    out.println("Content-type: " + content);

                    out.println(); // blank line between headers and content, very important !
                    out.flush(); // flush character output stream buffer

                    dataOut.write(fileData, 0, fileLength);
                    dataOut.flush();
                }catch (IOException e) {
                    main.getServer().getLogger().info("This is not an error and should not be reported.");
                    main.getServer().getLogger().info("Writing failed!");
                }
            }else if (method.equals("POST")) { // POST method so we return stuff.
                try {

                    byte[] fileData = readFileData(file, fileLength);

                    // send HTTP Headers
                    out.write("HTTP/1.1 200 OK");
                    out.write("Server: Java HTTP Server from SSaurel : 1.0");
                    out.println("Set-Cookie: Max-Age=0; Secure; HttpOnly");
                    out.println("Date: " + new Date());
                    out.println("Content-type: " + content);

                    out.println(); // blank line between headers and content, very important !
                    out.flush(); // flush character output stream buffer

                    dataOut.write(fileData, 0, fileLength);
                    dataOut.flush();
                }catch (IOException e) {
                    main.getServer().getLogger().info("This is not an error and should not be reported.");
                    main.getServer().getLogger().info("Writing failed!");
                }
            }
            out.close();
            in.close();
            sock.close();
        }catch (Exception e) {
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
