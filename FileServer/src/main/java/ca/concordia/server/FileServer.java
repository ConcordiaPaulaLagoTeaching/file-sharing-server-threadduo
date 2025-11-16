package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {
    // rw lock for synchronization
    private final Object ReadWritelock = new Object();
    private int num_activereaders = 0;
    private boolean InsideWriter = false;
    private int writerWaiting = 0; // kept for completeness, not heavily used

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize FileSystemManager
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }
    // Readers and writers synchronization 
    private void startRead() {
        synchronized (ReadWritelock) {
            // only block readers if writer is actively writing
            while (InsideWriter) {
                try { ReadWritelock.wait(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            num_activereaders++;
        }
    }

    private void endRead() {
        synchronized (ReadWritelock) {
            num_activereaders--;
            if (num_activereaders == 0) {
                ReadWritelock.notifyAll();
            }
        }
    }

    private void startWrite() {
        synchronized (ReadWritelock) {
            writerWaiting++;
            while (num_activereaders > 0 || InsideWriter) {
                try { ReadWritelock.wait(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            writerWaiting--;
            InsideWriter = true;
        }
    }

    private void endWrite() {
        synchronized (ReadWritelock) {
            InsideWriter = false;
            ReadWritelock.notifyAll();
        }
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
                    writer.println("ERROR");
                    writer.flush();
                    continue;
                }

                String[] parts = line.split(" ", 3);
                String command = parts[0].toUpperCase();

                try {
                    switch (command) {
                        case "CREATE":
                            if (parts.length < 2) {
                                writer.println("ERROR");
                                break;
                            }
                            startWrite();
                            try {
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                            } finally { endWrite(); }
                            break;

                        // write 
                        case "WRITE":
                            if (parts.length < 3) {
                                writer.println("ERROR");
                                break;
                            }
                            startWrite();
                            try {
                                fsManager.writeFile(parts[1], parts[2].getBytes());
                                writer.println("SUCCESS: wrote to '" + parts[1] + "'");
                            } finally { endWrite(); }
                            break;
                        // read 
                        case "READ":
                            if (parts.length < 2) {
                                writer.println("ERROR");
                                break;
                            }
                            startRead();
                            try {
                                byte[] fileData = fsManager.readFile(parts[1]);
                                writer.println("SUCCESS: " + new String(fileData));
                            } finally { endRead(); }
                            break;

                        // delete a file
                        case "DELETE":
                            if (parts.length < 2) {
                                writer.println("ERROR");
                                break;
                            }
                            startWrite();
                            try {
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                            } finally { endWrite(); }
                            break;

                        // list all files
                        case "LIST":
                            startRead();
                            try {
                                String[] files = fsManager.listFiles();
                                if (files.length == 0)
                                    writer.println("SUCCESS: (no files)");
                                else
                                    writer.println("SUCCESS: " + String.join(",", files));
                            } finally { endRead(); }
                            break;

                        case "QUIT":
                            writer.println("SUCCESS: Disconnecting.");
                            writer.flush();
                            return;
                        default:
                            writer.println("ERROR");
                            break;
                    }

                } catch (Exception ex) {
                    // error handling 
                    writer.println("ERROR");
                }

                writer.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (Exception e) {}
            System.err.println("Closed connection " + clientSocket);
        }
    }
    //multithreading
    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling new client: " + clientSocket);
                Thread Cthread = new Thread(() -> clientHandling(clientSocket));
                Cthread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}