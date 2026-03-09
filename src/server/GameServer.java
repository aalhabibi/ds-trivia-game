package server;

import java.io.*;
import java.net.*;

public class GameServer {
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("   MULTIPLAYER TRIVIA GAME SERVER");
        System.out.println("=========================================\n");

        try {
            // Load configuration
            System.out.println("[SERVER] Loading configuration...");
            GameConfig config = GameConfig.getInstance();
            config.load("data/config.properties");

            // Load users
            System.out.println("[SERVER] Loading user database...");
            UserManager userManager = UserManager.getInstance("data/users.txt");
            userManager.load();

            // Load questions
            System.out.println("[SERVER] Loading question bank...");
            QuestionBank questionBank = QuestionBank.getInstance("data/questions.txt");
            questionBank.load();

            // Load scores
            System.out.println("[SERVER] Loading score history...");
            ScoreManager scoreManager = ScoreManager.getInstance("data/scores.txt");
            scoreManager.load();

            // Initialize room manager
            RoomManager.getInstance();

            int port = config.getServerPort();
            System.out.println("\n[SERVER] Starting server on port " + port + "...");

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[SERVER] Server is running. Waiting for connections...\n");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVER] New connection from: "
                                       + clientSocket.getRemoteSocketAddress());
                    ClientHandler handler = new ClientHandler(clientSocket);
                    Thread thread = new Thread(handler, "Client-" + clientSocket.getPort());
                    thread.setDaemon(true);
                    thread.start();
                }
            }

        } catch (IOException e) {
            System.err.println("[SERVER] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
