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
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void clientHandling(Socket clientSocket){
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();
                        if (parts.length == 0 || command.isEmpty()) {
                            writer.println("error, bad command");
                            writer.flush();
                            continue;
                        }

                        switch (command) {
                            case "CREATE":
                                if (parts.length < 2) {
                                writer.println("ERROR: missing filename");
                                writer.flush();
                                break;
                                }
                                try {
                                    fsManager.createFile(parts[1]);
                                    writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                } catch (Exception ce) {
                                    writer.println("ERROR: " + ce.getMessage());
                                }
                                writer.flush();
                                break;
                            // read
                            case "READ":
                                if (parts.length < 2) {
                                    writer.println("ERROR: need a filename to read");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    byte[] readData = fsManager.readFile(parts[1]);
                                    writer.println("CONTENT: " + new String(readData));
                                } catch (Exception readErr) {
                                    writer.println("ERROR: " + readErr.getMessage());
                                }
                                writer.flush();
                                break;

                            // Write
                            case "WRITE":
                                if (parts.length < 3) {
                                    writer.println("ERROR: need filename + text");
                                    writer.flush();
                                    break;
                                }

                                // collect all text after filename
                                StringBuilder sb = new StringBuilder();
                                for (int i = 2; i < parts.length; i++) {
                                    sb.append(parts[i]);
                                    if (i < parts.length - 1) sb.append(" ");
                                }

                                try {
                                    fsManager.writeFile(parts[1], sb.toString().getBytes());
                                    writer.println("SUCCESS: wrote to '" + parts[1] + "'");
                                } catch (Exception wErr) {
                                    writer.println("ERROR: " + wErr.getMessage());
                                }
                                writer.flush();
                                break;

                            // delete
                            case "DELETE":
                                if (parts.length < 2) {
                                    writer.println("ERROR: missing filename to delete");
                                    writer.flush();
                                    break;
                                }

                                try {
                                    fsManager.deleteFile(parts[1]);
                                    writer.println("SUCCESS: deleted '" + parts[1] + "'");
                                } catch (Exception dErr) {
                                    writer.println("ERROR: " + dErr.getMessage());
                                }
                                writer.flush();
                                break;

                            // list
                            case "LIST":
                                String[] all = fsManager.listFiles();
                                if (all.length == 0) {
                                    writer.println("No files right now.");
                                } else {
                                    StringBuilder out = new StringBuilder();
                                    for (int i = 0; i < all.length; i++) {
                                        out.append(all[i]);
                                        if (i < all.length - 1) out.append(", ");
                                    }
                                    writer.println("FILES: " + out);
                                }
                                writer.flush();
                                break;

                            case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                return;
                            default:
                                writer.println("ERROR: Unknown command.");
                                break;
                        }
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
                Thread Cthread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        clientHandling(clientSocket);
                    }
                });
                Cthread.start();
    }
       } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}