package server;

import model.Question;
import model.ScoreEntry;
import model.User;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.util.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private User user;
    private volatile GameRoom currentRoom;
    private volatile boolean connected = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return user != null ? user.getUsername() : "unknown";
    }

    public synchronized void sendMessage(String message) {
        if (writer != null && connected) {
            writer.println(message);
            writer.flush();
        }
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("[SERVER] Client connected: " + socket.getRemoteSocketAddress());

            // Authentication phase
            if (!handleAuth()) {
                return;
            }

            System.out.println("[SERVER] User logged in: " + user.getUsername());

            // Main menu loop
            mainMenuLoop();

        } catch (IOException e) {
            System.out.println("[SERVER] Connection error for "
                    + getUsername() + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ===================== AUTHENTICATION =====================

    private boolean handleAuth() throws IOException {
        sendMessage("=========================================");
        sendMessage("   WELCOME TO THE MULTIPLAYER TRIVIA GAME");
        sendMessage("=========================================");

        while (connected) {
            sendMessage("\n1. Login");
            sendMessage("2. Register");
            sendMessage("Enter choice:");

            String choice = readLine();
            if (choice == null)
                return false;
            choice = choice.trim();

            switch (choice) {
                case "1":
                    if (handleLogin())
                        return true;
                    break;
                case "2":
                    if (handleRegister())
                        return true;
                    break;
                default:
                    sendMessage("Invalid choice. Please enter 1 or 2.");
            }
        }
        return false;
    }

    private boolean handleLogin() throws IOException {
        sendMessage("Enter username:");
        String username = readLine();
        if (username == null)
            return false;
        username = username.trim();

        sendMessage("Enter password:");
        String password = readLine();
        if (password == null)
            return false;
        password = password.trim();

        try {
            user = UserManager.getInstance().authenticate(username, password);
            sendMessage("\nLogin successful! Welcome back, " + user.getName() + "!");
            return true;
        } catch (UserManager.UserNotFoundException e) {
            sendMessage("\nERROR 404: Username not found.");
            return false;
        } catch (UserManager.UnauthorizedException e) {
            sendMessage("\nERROR 401: Unauthorized - wrong password.");
            return false;
        } catch (UserManager.AlreadyLoggedInException e) {
            sendMessage("\nERROR 409: User is already logged in from another client.");
            return false;
        }
    }

    private boolean handleRegister() throws IOException {
        sendMessage("Enter your full name:");
        String name = readLine();
        if (name == null)
            return false;
        name = name.trim();

        sendMessage("Choose a username:");
        String username = readLine();
        if (username == null)
            return false;
        username = username.trim();

        if (username.isEmpty()) {
            sendMessage("Username cannot be empty.");
            return false;
        }

        sendMessage("Choose a password:");
        String password = readLine();
        if (password == null)
            return false;
        password = password.trim();

        if (password.isEmpty()) {
            sendMessage("Password cannot be empty.");
            return false;
        }

        try {
            user = UserManager.getInstance().register(name, username, password);
            sendMessage("\nRegistration successful! Welcome, " + user.getName() + "!");
            return true;
        } catch (UserManager.UsernameExistsException e) {
            sendMessage("\nERROR 409: Username '" + username + "' is already taken.");
            return false;
        }
    }

    // ===================== MAIN MENU =====================

    private void mainMenuLoop() throws IOException {
        while (connected) {
            sendMessage("\n=========================================");
            sendMessage("          MAIN MENU");
            sendMessage("  Welcome, " + user.getName() + "!");
            sendMessage("=========================================");
            sendMessage("1. Single Player");
            sendMessage("2. Multiplayer (Teams)");
            sendMessage("3. View Score History");
            sendMessage("4. Quit");
            sendMessage("Enter choice:");

            String choice = readLine();
            if (choice == null)
                break;
            choice = choice.trim();

            switch (choice) {
                case "1":
                    handleSinglePlayer();
                    break;
                case "2":
                    handleMultiplayer();
                    break;
                case "3":
                    handleScoreHistory();
                    break;
                case "4":
                case "-":
                    sendMessage("Goodbye, " + user.getName() + "!");
                    return;
                default:
                    sendMessage("Invalid choice. Please enter 1-4.");
            }
        }
    }

    // ===================== SCORE HISTORY =====================

    private void handleScoreHistory() {
        List<ScoreEntry> history = ScoreManager.getInstance().getHistory(user.getUsername());
        if (history.isEmpty()) {
            sendMessage("\nNo game history found.");
            return;
        }

        sendMessage("\n--- Score History for " + user.getName() + " ---");
        for (int i = history.size() - 1; i >= 0; i--) {
            sendMessage("  " + history.get(i).getDisplayString());
        }
        sendMessage("---");
    }

    // ===================== SINGLE PLAYER =====================

    private void handleSinglePlayer() throws IOException {
        QuestionBank bank = QuestionBank.getInstance();

        // Choose category
        Set<String> categories = bank.getCategories();
        if (categories.isEmpty()) {
            sendMessage("No questions available in the bank.");
            return;
        }

        List<String> catList = new ArrayList<>(categories);
        sendMessage("\nSelect category:");
        for (int i = 0; i < catList.size(); i++) {
            sendMessage("  " + (i + 1) + ". " + catList.get(i));
        }
        sendMessage("  " + (catList.size() + 1) + ". All Categories");
        sendMessage("Enter choice (or '-' to go back):");

        String input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        int catChoice;
        try {
            catChoice = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            sendMessage("Invalid input.");
            return;
        }

        String category = null; // null = all
        if (catChoice >= 1 && catChoice <= catList.size()) {
            category = catList.get(catChoice - 1);
        } else if (catChoice != catList.size() + 1) {
            sendMessage("Invalid category choice.");
            return;
        }

        // Choose difficulty
        sendMessage("\nSelect difficulty:");
        sendMessage("  1. Easy");
        sendMessage("  2. Medium");
        sendMessage("  3. Hard");
        sendMessage("  4. Mixed");
        sendMessage("Enter choice (or '-' to go back):");

        input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        String difficulty = null; // null = mixed
        switch (input.trim()) {
            case "1":
                difficulty = "easy";
                break;
            case "2":
                difficulty = "medium";
                break;
            case "3":
                difficulty = "hard";
                break;
            case "4":
                difficulty = null;
                break;
            default:
                sendMessage("Invalid difficulty choice.");
                return;
        }

        // Show available questions count
        int available = bank.getAvailableCount(category, difficulty);
        if (available == 0) {
            sendMessage("No questions available for the selected criteria.");
            return;
        }

        sendMessage("\nAvailable questions: " + available);
        sendMessage("Enter number of questions (1-" + available + "):");

        input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        int numQuestions;
        try {
            numQuestions = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            sendMessage("Invalid number.");
            return;
        }
        numQuestions = Math.max(1, Math.min(available, numQuestions));

        // Get questions
        List<Question> questions = bank.getQuestions(category, difficulty, numQuestions);
        if (questions.isEmpty()) {
            sendMessage("No questions found.");
            return;
        }

        // Play the game
        playSinglePlayerGame(questions);
    }

    private void playSinglePlayerGame(List<Question> questions) throws IOException {
        int score = 0;
        int correct = 0;
        List<String[]> results = new ArrayList<>();
        int timeLimit = GameConfig.getInstance().getQuestionTimeSeconds();

        sendMessage("\n============================================");
        sendMessage("       SINGLE PLAYER GAME STARTING!");
        sendMessage("  Questions: " + questions.size()
                + " | Time per question: " + timeLimit + "s");
        sendMessage("  Type '-' at any time to quit the game.");
        sendMessage("============================================");

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            sendMessage(q.getDisplayText(i + 1, questions.size()));
            sendMessage("Enter your answer (A/B/C/D):");

            String answer = readAnswerWithTimeout(timeLimit);

            if (answer != null && answer.equals("-")) {
                sendMessage("\nYou quit the game.");
                break;
            }

            boolean isCorrect = answer != null
                    && answer.equalsIgnoreCase(q.getCorrectAnswer());
            String status;
            if (answer == null) {
                status = "timeout";
                sendMessage("\n>> Time's up! Correct answer: " + q.getCorrectChoiceText());
            } else if (isCorrect) {
                status = "correct";
                score += q.getPoints();
                correct++;
                sendMessage("\n[CORRECT] +" + q.getPoints() + " points!");
            } else {
                status = "wrong";
                sendMessage("\n[WRONG] Correct answer: " + q.getCorrectChoiceText());
            }

            sendMessage("Current score: " + score + " (" + correct
                    + "/" + (i + 1) + " correct)");

            results.add(new String[] { q.getText(), answer != null ? answer : "-",
                    q.getCorrectAnswer(), status });

            if (i < questions.size() - 1) {
                sendMessage("\nNext question in 2 seconds...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Show final results
        sendMessage("\n\n================================================");
        sendMessage("            GAME OVER - RESULTS");
        sendMessage("================================================");
        sendMessage("Final Score: " + score + " (" + correct
                + "/" + results.size() + " correct)\n");

        for (int i = 0; i < results.size(); i++) {
            String[] r = results.get(i);
            String statusTag;
            switch (r[3]) {
                case "correct":
                    statusTag = "[CORRECT]";
                    break;
                case "wrong":
                    statusTag = "[WRONG]";
                    break;
                default:
                    statusTag = "[TIMEOUT]";
                    break;
            }
            sendMessage("  Q" + (i + 1) + ": " + r[0]);
            sendMessage("       Your answer: " + r[1]
                    + " | Correct: " + r[2] + " " + statusTag);
        }
        sendMessage("\n================================================");

        // Save score
        ScoreEntry entry = new ScoreEntry(
                user.getUsername(), LocalDate.now().toString(),
                "single", score, correct, results.size(), "");
        ScoreManager.getInstance().addScore(entry);
        sendMessage("Score saved to history.");
    }

    private String readAnswerWithTimeout(int seconds) throws IOException {
        int previousTimeout = 0;
        try {
            previousTimeout = socket.getSoTimeout();
            socket.setSoTimeout(1000);
        } catch (SocketException e) {
            // ignore
        }

        long deadline = System.currentTimeMillis() + (long) seconds * 1000;
        Set<Integer> warned = new HashSet<>();
        int[] warnings = { 15, 10, 5, 3, 1 };

        try {
            while (true) {
                long remainingMs = deadline - System.currentTimeMillis();
                if (remainingMs <= 0)
                    return null; // timed out

                int remainingSec = (int) (remainingMs / 1000);

                for (int w : warnings) {
                    if (remainingSec == w && !warned.contains(w)) {
                        sendMessage(">> " + w + " seconds left!");
                        warned.add(w);
                    }
                }

                try {
                    String input = reader.readLine();
                    if (input == null) {
                        connected = false;
                        return null;
                    }
                    input = input.trim();
                    if ("-".equals(input))
                        return "-";
                    String normalized = input.toUpperCase();
                    if (normalized.matches("[ABCD]")) {
                        return normalized;
                    }
                    sendMessage("Invalid input. Enter A, B, C, or D (or '-' to quit):");
                } catch (SocketTimeoutException e) {
                    // continue loop
                }
            }
        } finally {
            try {
                socket.setSoTimeout(previousTimeout);
            } catch (SocketException e) {
                // ignore
            }
        }
    }

    // ===================== MULTIPLAYER =====================

    private void handleMultiplayer() throws IOException {
        while (connected) {
            sendMessage("\n--- MULTIPLAYER ---");
            sendMessage("1. Create Room");
            sendMessage("2. Join Room");
            sendMessage("3. List Available Rooms");
            sendMessage("4. Back to Main Menu");
            sendMessage("Enter choice:");

            String choice = readLine();
            if (choice == null)
                return;
            choice = choice.trim();

            switch (choice) {
                case "1":
                    handleCreateRoom();
                    break;
                case "2":
                    handleJoinRoom();
                    break;
                case "3":
                    handleListRooms();
                    break;
                case "4":
                case "-":
                    return;
                default:
                    sendMessage("Invalid choice.");
            }
        }
    }

    private void handleListRooms() {
        List<GameRoom> rooms = RoomManager.getInstance().getWaitingRooms();
        if (rooms.isEmpty()) {
            sendMessage("\nNo rooms currently available.");
        } else {
            sendMessage("\n--- Available Rooms ---");
            for (int i = 0; i < rooms.size(); i++) {
                sendMessage("  " + (i + 1) + ". " + rooms.get(i).getListDisplay());
            }
            sendMessage("---");
        }
    }

    private void handleCreateRoom() throws IOException {
        sendMessage("\nEnter room name (or '-' to cancel):");
        String roomName = readLine();
        if (roomName == null || "-".equals(roomName.trim()))
            return;
        roomName = roomName.trim();
        if (roomName.isEmpty()) {
            sendMessage("Room name cannot be empty.");
            return;
        }

        sendMessage("Enter your team name:");
        String teamName = readLine();
        if (teamName == null || "-".equals(teamName.trim()))
            return;
        teamName = teamName.trim();
        if (teamName.isEmpty()) {
            sendMessage("Team name cannot be empty.");
            return;
        }

        // Choose category
        QuestionBank bank = QuestionBank.getInstance();
        Set<String> categories = bank.getCategories();
        List<String> catList = new ArrayList<>(categories);
        sendMessage("\nSelect category:");
        for (int i = 0; i < catList.size(); i++) {
            sendMessage("  " + (i + 1) + ". " + catList.get(i));
        }
        sendMessage("  " + (catList.size() + 1) + ". All Categories");
        sendMessage("Enter choice:");

        String input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        int catChoice;
        try {
            catChoice = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            sendMessage("Invalid input.");
            return;
        }

        String category;
        if (catChoice >= 1 && catChoice <= catList.size()) {
            category = catList.get(catChoice - 1);
        } else if (catChoice == catList.size() + 1) {
            category = "All";
        } else {
            sendMessage("Invalid choice.");
            return;
        }

        // Choose difficulty
        sendMessage("\nSelect difficulty:");
        sendMessage("  1. Easy");
        sendMessage("  2. Medium");
        sendMessage("  3. Hard");
        sendMessage("  4. Mixed");
        sendMessage("Enter choice:");

        input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        String difficulty;
        switch (input.trim()) {
            case "1":
                difficulty = "easy";
                break;
            case "2":
                difficulty = "medium";
                break;
            case "3":
                difficulty = "hard";
                break;
            case "4":
                difficulty = "Mixed";
                break;
            default:
                sendMessage("Invalid choice.");
                return;
        }

        // Number of questions
        String cat = category.equalsIgnoreCase("All") ? null : category;
        String diff = difficulty.equalsIgnoreCase("Mixed") ? null : difficulty;
        int available = bank.getAvailableCount(cat, diff);
        if (available == 0) {
            sendMessage("No questions available for the selected criteria.");
            return;
        }

        sendMessage("\nAvailable questions: " + available);
        sendMessage("Enter number of questions (1-" + available + "):");

        input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        int numQuestions;
        try {
            numQuestions = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            sendMessage("Invalid number.");
            return;
        }
        numQuestions = Math.max(1, Math.min(available, numQuestions));

        // Create the room
        String error = RoomManager.getInstance().createRoom(
                roomName, this, teamName, category, difficulty, numQuestions);
        if (error != null) {
            sendMessage("Error: " + error);
            return;
        }

        currentRoom = RoomManager.getInstance().getRoom(roomName);
        sendMessage("\nRoom '" + roomName + "' created!"
                + " Your team: '" + teamName + "'");
        sendMessage(currentRoom.getStatusDisplay());

        // Enter lobby
        enterLobbyAsCreator();
    }

    private void enterLobbyAsCreator() throws IOException {
        sendMessage("\nWaiting for players to join...");
        sendMessage("Commands: 'start' - start game | 'status' - room info | '-' - cancel room");

        int prevTimeout = 0;
        try {
            prevTimeout = socket.getSoTimeout();
            socket.setSoTimeout(2000);
        } catch (SocketException e) {
            /* ignore */ }

        try {
            while (currentRoom != null
                    && currentRoom.getState() == GameRoom.RoomState.WAITING
                    && connected) {
                try {
                    String input = reader.readLine();
                    if (input == null) {
                        connected = false;
                        break;
                    }
                    input = input.trim();

                    if ("-".equals(input)) {
                        currentRoom.disband();
                        currentRoom = null;
                        sendMessage("Room disbanded.");
                        return;
                    } else if ("start".equalsIgnoreCase(input)) {
                        String err = currentRoom.tryStart();
                        if (err != null) {
                            sendMessage("Cannot start: " + err);
                        }
                        // If started, the loop condition will be false
                    } else if ("status".equalsIgnoreCase(input)) {
                        sendMessage(currentRoom.getStatusDisplay());
                    } else if (!input.isEmpty()) {
                        sendMessage("Unknown command. Use 'start', 'status', or '-'.");
                    }
                } catch (SocketTimeoutException e) {
                    // Continue loop
                }
            }
        } finally {
            try {
                socket.setSoTimeout(prevTimeout);
            } catch (SocketException e) {
                /* ignore */ }
        }

        // If game started, enter game mode
        if (currentRoom != null
                && currentRoom.getState() == GameRoom.RoomState.IN_PROGRESS) {
            enterGameMode();
        }
    }

    private void handleJoinRoom() throws IOException {
        // List available rooms
        handleListRooms();

        List<GameRoom> rooms = RoomManager.getInstance().getWaitingRooms();
        if (rooms.isEmpty())
            return;

        sendMessage("\nEnter room name to join (or '-' to cancel):");
        String roomName = readLine();
        if (roomName == null || "-".equals(roomName.trim()))
            return;
        roomName = roomName.trim();

        GameRoom room = RoomManager.getInstance().getRoom(roomName);
        if (room == null) {
            sendMessage("Room '" + roomName + "' not found.");
            return;
        }
        if (room.getState() != GameRoom.RoomState.WAITING) {
            sendMessage("Room is no longer accepting players.");
            return;
        }

        // Choose team
        sendMessage(room.getStatusDisplay());
        sendMessage("\nOptions:");
        sendMessage("  1. Join Team '" + room.getTeam1().getName() + "'");
        if (room.getTeam2() != null) {
            sendMessage("  2. Join Team '" + room.getTeam2().getName() + "'");
        } else {
            sendMessage("  2. Create a new team (Team 2)");
        }
        sendMessage("Enter choice (or '-' to cancel):");

        String input = readLine();
        if (input == null || "-".equals(input.trim()))
            return;

        switch (input.trim()) {
            case "1": {
                String err = room.joinTeam(1, this);
                if (err != null) {
                    sendMessage("Error: " + err);
                    return;
                }
                break;
            }
            case "2": {
                if (room.getTeam2() != null) {
                    String err = room.joinTeam(2, this);
                    if (err != null) {
                        sendMessage("Error: " + err);
                        return;
                    }
                } else {
                    sendMessage("Enter your team name:");
                    String teamName = readLine();
                    if (teamName == null || "-".equals(teamName.trim()))
                        return;
                    teamName = teamName.trim();
                    if (teamName.isEmpty()) {
                        sendMessage("Team name cannot be empty.");
                        return;
                    }
                    String err = room.createTeam2(teamName, this);
                    if (err != null) {
                        sendMessage("Error: " + err);
                        return;
                    }
                }
                break;
            }
            default:
                sendMessage("Invalid choice.");
                return;
        }

        currentRoom = room;
        sendMessage(room.getStatusDisplay());

        // Enter lobby as joiner
        enterLobbyAsJoiner();
    }

    private void enterLobbyAsJoiner() throws IOException {
        sendMessage("\nWaiting for the room creator to start the game...");
        sendMessage("Type '-' to leave the room.");

        int prevTimeout = 0;
        try {
            prevTimeout = socket.getSoTimeout();
            socket.setSoTimeout(2000);
        } catch (SocketException e) {
            /* ignore */ }

        try {
            while (currentRoom != null
                    && currentRoom.getState() == GameRoom.RoomState.WAITING
                    && connected) {
                try {
                    String input = reader.readLine();
                    if (input == null) {
                        connected = false;
                        break;
                    }
                    input = input.trim();

                    if ("-".equals(input)) {
                        currentRoom.removePlayer(this);
                        currentRoom = null;
                        sendMessage("You left the room.");
                        return;
                    } else if ("status".equalsIgnoreCase(input)) {
                        sendMessage(currentRoom.getStatusDisplay());
                    }
                } catch (SocketTimeoutException e) {
                    // Continue loop
                }
            }
        } finally {
            try {
                socket.setSoTimeout(prevTimeout);
            } catch (SocketException e) {
                /* ignore */ }
        }

        // If game started, enter game mode
        if (currentRoom != null
                && currentRoom.getState() == GameRoom.RoomState.IN_PROGRESS) {
            enterGameMode();
        }
    }

    private void enterGameMode() throws IOException {
        int prevTimeout = 0;
        try {
            prevTimeout = socket.getSoTimeout();
            socket.setSoTimeout(1000);
        } catch (SocketException e) {
            /* ignore */ }

        try {
            while (currentRoom != null
                    && currentRoom.getState() == GameRoom.RoomState.IN_PROGRESS
                    && connected) {
                try {
                    String input = reader.readLine();
                    if (input == null) {
                        connected = false;
                        break;
                    }
                    input = input.trim();

                    if ("-".equals(input)) {
                        currentRoom.removePlayer(this);
                        sendMessage("You left the game.");
                        currentRoom = null;
                        return;
                    }

                    if (!input.isEmpty()) {
                        currentRoom.submitAnswer(this, input);
                    }
                } catch (SocketTimeoutException e) {
                    // Continue loop
                }
            }
        } finally {
            try {
                socket.setSoTimeout(prevTimeout);
            } catch (SocketException e) {
                /* ignore */ }
        }

        currentRoom = null;
    }

    // ===================== UTILITY =====================

    private String readLine() throws IOException {
        try {
            String line = reader.readLine();
            if (line == null) {
                connected = false;
            }
            return line;
        } catch (SocketException e) {
            connected = false;
            return null;
        }
    }

    private void cleanup() {
        connected = false;
        System.out.println("[SERVER] Client disconnected: " + getUsername());

        if (user != null) {
            UserManager.getInstance().logout(user.getUsername());
        }

        // Remove from room if in one
        if (currentRoom != null) {
            currentRoom.removePlayer(this);
            currentRoom = null;
        }

        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
