package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    private void clientHandling(Socket clientSocket){
        System.out.println("Handling client: " + clientSocket);
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Received from client: " + line);

                line = line.trim();
                if (line.isEmpty()) {
                    writer.println("ERROR: empty command");
                    writer.flush();
                    continue;
                }

                String[] parts = line.split(" ",3);
                String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "CREATE":
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            fsManager.createFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' created.");
                            break;

                        // write 
                        case "WRITE":
                            if (parts.length < 3) {
                                writer.println("ERROR: missing data to write");
                                break;
                            }
                            fsManager.writeFile(parts[1], parts[2].getBytes());
                            writer.println("SUCCESS: wrote to '" + parts[1] + "'");
                            break;
                        // read 
                        case "READ":
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            byte[] fileData = fsManager.readFile(parts[1]);
                            writer.println("SUCCESS: " + new String(fileData));
                            break;

                        // delete a file
                        case "DELETE":
                            if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                break;
                            }
                            fsManager.deleteFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                            break;

                        // list all files
                        case "LIST":
                            String[] files = fsManager.listFiles();
                            if (files.length == 0) {
                                writer.println("SUCCESS: (no files)");
                            } else {
                                writer.println("SUCCESS: " + String.join(",", files));
                            }
                            break;

                        case "QUIT":
                            writer.println("SUCCESS: Disconnecting.");
                            writer.flush();
                            return;
                        default:
                            writer.println("ERROR: Unknown command.");
                            break;
                    }

                } catch (Exception ex) {
                    //exception returns an error line, no killing server
                    writer.println("ERROR: " + ex.getMessage());
                }

                writer.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception e) {
                // Ignore
            }
            System.err.println("Closed connection " + clientSocket);
        }
    }   
    //multithreading
    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling new client: " + clientSocket);
                //new thread for each client
                Thread Cthread = new Thread(() -> clientHandling(clientSocket));
                Cthread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}