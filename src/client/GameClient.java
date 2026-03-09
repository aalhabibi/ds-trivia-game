package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class GameClient {
    private static volatile boolean running = true;

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;

        // Allow host and port from command line args
        if (args.length >= 1)
            host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: 12345");
            }
        }

        System.out.println("Connecting to server at " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected!\n");

            BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter serverWriter = new PrintWriter(
                    socket.getOutputStream(), true);

            // Thread to read from server and display
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while (running && (line = serverReader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("\nDisconnected from server.");
                    }
                } finally {
                    running = false;
                }
            }, "ServerReader");
            readerThread.setDaemon(true);
            readerThread.start();

            // Main thread: read from stdin and send to server
            Scanner stdin = new Scanner(System.in);
            try {
                while (running) {
                    if (stdin.hasNextLine()) {
                        String input = stdin.nextLine();
                        if (!running)
                            break;
                        serverWriter.println(input);

                        // Flush to ensure immediate delivery
                        if (serverWriter.checkError()) {
                            System.out.println("\nConnection to server lost.");
                            break;
                        }
                    } else {
                        // stdin closed
                        break;
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.out.println("\nError: " + e.getMessage());
                }
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to server at " + host + ":" + port);
            System.err.println("Make sure the server is running.");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }

        System.out.println("Client exiting.");
    }
}
