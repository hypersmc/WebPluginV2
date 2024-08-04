package me.jumpwatch.webserver.php.linux;

import me.jumpwatch.webserver.WebCoreProxy;
import me.jumpwatch.webserver.utils.ContentTypeResolver;

import java.io.*;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * @author JumpWatch on 03-08-2024
 * @Project WebPluginV2
 * v1.0.0
 */
public class PHPWebServerBun extends Thread{
    Socket socket;
    WebCoreProxy main;
    String DEFAULT_FILE = "index.php";
    public PHPWebServerBun(Socket socket, WebCoreProxy main) {
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
            boolean debug = (Boolean) main.configuration.getBoolean("Settings.debug");
            if (debug) e.printStackTrace();
        }
    }
    private void processRequest(BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) {
        String fileRequested;
        String method;
        try {
            String input = in.readLine();
            if (input == null || input.isEmpty()) {
                return; // no input, exit early
            }

            StringTokenizer parse = new StringTokenizer(input);
            method = parse.nextToken().toUpperCase();
            fileRequested = parse.nextToken().toLowerCase();
            String s;
            int counter = 0, contentLength = 0;

            // Reading headers
            while (!(s = in.readLine()).equals("")) {
                if (counter == 0 && s.equalsIgnoreCase(WebCoreProxy.closeConnection)) {
                    closeSocket();
                    return;
                }
                if (s.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(s.split("Length: ")[1]);
                }
                counter++;
            }

            // Handle different HTTP methods
            switch (method) {
                case "GET":
                    handleGetRequest(fileRequested, out, dataOut);
                    break;
                case "POST":
                    String postData = "";
                    if (contentLength > 0) {
                        char[] charArray = new char[contentLength];
                        in.read(charArray, 0, contentLength);
                        postData = new String(charArray);
                    }
                    handlePostRequest(fileRequested, postData, out, dataOut);
                    break;
                default:
                    // Handle other HTTP methods if needed
                    send405Response(out, dataOut);
                    break;
            }
        } catch (IOException e) {
            main.getLogger().info("Error processing request: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }
    private void handleGetRequest(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        if (fileRequested.endsWith("/")) {
            fileRequested += DEFAULT_FILE;
        }

        File file = new File(main.getDataFolder() + "/php/", fileRequested);
        if (!file.exists()) {
            send404Response(out, dataOut);
            return;
        }

        if (fileRequested.endsWith(".php")) {
            // Handle PHP execution
            String phpOutput = executePHP(file);
            int fileLength = phpOutput.length();
            String content = "text/html";

            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Server: Java HTTP Server from WebPlugin : " + main.pluginversion + "\r\n");
            out.println("Set-Cookie: Max-Age=0; HttpOnly");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content + "\r\n");
//            out.println("Content-length: " + fileLength + "\r\n");
            out.println();
            out.flush();

            dataOut.write(phpOutput.getBytes(), 0, fileLength);
            dataOut.flush();
        } else {
            // Handle static content
            int fileLength = (int) file.length();
            byte[] fileData = readFileData(file, fileLength);
            String content = new ContentTypeResolver().getContentType(fileRequested);

            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Server: Java HTTP Server from WebPlugin : " + main.pluginversion + "\r\n");
            out.println("Set-Cookie: Max-Age=0; HttpOnly");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content + "\r\n");
//            out.println("Content-length: " + fileLength + "\r\n");
            out.println();
            out.flush();

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        }
    }

    private void handlePostRequest(String fileRequested, String postData, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        File file = new File(main.getDataFolder() + "/php/", fileRequested);
        if (!file.exists()) {
            send404Response(out, dataOut);
            return;
        }

        if (fileRequested.endsWith(".php")) {
            // Pass POST data to PHP script (use stdin or env variables as needed)
            String[] cmd = {"/bin/sh", "-c", "echo \"" + postData + "\" | ~/plugins/webplugin/phplinux/bin/php8/bin/php " + file.getAbsolutePath()};
            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdInput.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = stdError.readLine()) != null) {
                output.append(line).append("\n");
            }

            try {
                p.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String phpOutput = output.toString();
            int fileLength = phpOutput.length();
            String content = "text/html";

            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Server: Java HTTP Server from WebPlugin : " + main.pluginversion + "\r\n");
            out.println("Set-Cookie: Max-Age=0; HttpOnly");
            out.println("Date: " + new Date());
            out.println("Content-type: " + content + "\r\n");
//            out.println("Content-length: " + fileLength + "\r\n");
            out.println();
            out.flush();

            dataOut.write(phpOutput.getBytes(), 0, fileLength);
            dataOut.flush();
        } else {
            // Handle POST for non-PHP files if needed (e.g., file uploads)
            send405Response(out, dataOut);
        }
    }

    private void send404Response(PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        String errorMessage = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: 22\r\n" +
                "\r\n" +
                "<h1>404 Not Found</h1>";
        out.write(errorMessage);
        out.flush();
        dataOut.write(errorMessage.getBytes(), 0, errorMessage.length());
        dataOut.flush();
    }

    private void send405Response(PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        String errorMessage = "HTTP/1.1 405 Method Not Allowed\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: 31\r\n" +
                "\r\n" +
                "<h1>405 Method Not Allowed</h1>";
        out.write(errorMessage);
        out.flush();
        dataOut.write(errorMessage.getBytes(), 0, errorMessage.length());
        dataOut.flush();
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
    // Function to execute the PHP script using PHP CLI and return the output
    private String executePHP(File phpFile) throws IOException {
        // Create the PHP content to set SERVER_SOFTWARE dynamically and include the original PHP script
        String modifiedContent = "<?php\n"
                + "$_SERVER['SERVER_SOFTWARE'] = 'WebPlugin 2.5R';\n"
                + "include '" + phpFile.getAbsolutePath() + "';\n"
                + "?>";

        // Execute PHP with the modified content using ProcessBuilder
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "cd ~/plugins/webplugin/phplinux/bin/php8/bin/ && ./php");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Write the modified content to the PHP process's standard input
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(modifiedContent);
            writer.flush();
        }

        // Read the PHP process's output and error streams
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        StringBuilder output = new StringBuilder();
        String line;

        // Patterns to match the specific warning and content-length
        String warningPattern = "Warning: Failed loading Zend extension 'opcache.so'";
        Pattern contentLengthPattern = Pattern.compile("Content-length:\\s*\\d+");

        // Read standard output (PHP script output)
        while ((line = stdInput.readLine()) != null) {
            if (!line.contains(warningPattern) && !contentLengthPattern.matcher(line).find()) {
                output.append(line).append("\n");
            }
        }

        // Read error output (warnings, errors)
        while ((line = stdError.readLine()) != null) {
            if (!line.contains(warningPattern) && !contentLengthPattern.matcher(line).find()) {
                output.append(line).append("\n");
            }
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return output.toString();
    }
}
