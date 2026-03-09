package server;

import model.Question;
import model.ScoreEntry;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameRoom {
    public enum RoomState { WAITING, IN_PROGRESS, FINISHED }

    private final String name;
    private final ClientHandler creator;
    private final Team team1;
    private Team team2;
    private final String category;
    private final String difficulty;
    private final int numQuestions;
    private volatile RoomState state;

    // Game state
    private List<Question> questions;
    private int currentQuestionIndex;
    private volatile boolean acceptingAnswers;
    private final Map<ClientHandler, String> currentAnswers = new ConcurrentHashMap<>();
    private final Map<ClientHandler, Integer> playerScores = new ConcurrentHashMap<>();
    private final Map<ClientHandler, List<String[]>> playerResults = new ConcurrentHashMap<>();
    // Each String[]: {questionText, playerAnswer, correctAnswer, "correct"/"wrong"/"timeout"}

    public GameRoom(String name, ClientHandler creator, String team1Name,
                    String category, String difficulty, int numQuestions) {
        this.name = name;
        this.creator = creator;
        this.team1 = new Team(team1Name);
        this.team1.addPlayer(creator);
        this.category = category;
        this.difficulty = difficulty;
        this.numQuestions = numQuestions;
        this.state = RoomState.WAITING;
    }

    // --- Getters ---
    public String getName() { return name; }
    public ClientHandler getCreator() { return creator; }
    public Team getTeam1() { return team1; }
    public Team getTeam2() { return team2; }
    public String getCategory() { return category; }
    public String getDifficulty() { return difficulty; }
    public int getNumQuestions() { return numQuestions; }
    public RoomState getState() { return state; }

    public List<ClientHandler> getAllPlayers() {
        List<ClientHandler> all = new ArrayList<>(team1.getPlayers());
        if (team2 != null) {
            all.addAll(team2.getPlayers());
        }
        return all;
    }

    // --- Team management ---

    public synchronized String createTeam2(String teamName, ClientHandler player) {
        if (team2 != null) {
            return "Team 2 already exists: " + team2.getName();
        }
        if (teamName.equalsIgnoreCase(team1.getName())) {
            return "Team name must be different from Team 1.";
        }
        team2 = new Team(teamName);
        team2.addPlayer(player);
        broadcastToAll("[ROOM] " + player.getUsername() + " created Team '"
                       + teamName + "' and joined it.");
        return null;
    }

    public synchronized String joinTeam(int teamNumber, ClientHandler player) {
        GameConfig config = GameConfig.getInstance();
        Team team = (teamNumber == 1) ? team1 : team2;

        if (team == null) {
            return "Team " + teamNumber + " does not exist yet.";
        }
        if (team.getPlayerCount() >= config.getMaxPlayersPerTeam()) {
            return "Team '" + team.getName() + "' is full ("
                   + config.getMaxPlayersPerTeam() + " max).";
        }
        if (team.hasPlayer(player)) {
            return "You are already on this team.";
        }

        team.addPlayer(player);
        broadcastToAll("[ROOM] " + player.getUsername() + " joined Team '"
                       + team.getName() + "'.");
        return null;
    }

    public synchronized void removePlayer(ClientHandler player) {
        boolean wasInTeam1 = false;
        if (team1.hasPlayer(player)) {
            team1.removePlayer(player);
            wasInTeam1 = true;
        }
        if (team2 != null && team2.hasPlayer(player)) {
            team2.removePlayer(player);
        }

        broadcastToAll("[ROOM] " + player.getUsername() + " left the room.");

        // If room is empty or creator left during WAITING, disband
        if (state == RoomState.WAITING) {
            if (getAllPlayers().isEmpty()) {
                disband();
            }
        }
    }

    public void disband() {
        broadcastToAll("[ROOM] Room '" + name + "' has been disbanded.");
        state = RoomState.FINISHED;
        RoomManager.getInstance().removeRoom(name);
    }

    // --- Game start ---

    public synchronized String tryStart() {
        GameConfig config = GameConfig.getInstance();

        if (team2 == null || team2.getPlayerCount() == 0) {
            return "Team 2 has not been formed yet. Need an opposing team to start.";
        }
        if (team1.getPlayerCount() != team2.getPlayerCount()) {
            return "Teams must have equal players. '"
                   + team1.getName() + "': " + team1.getPlayerCount()
                   + ", '" + team2.getName() + "': " + team2.getPlayerCount();
        }
        if (team1.getPlayerCount() < config.getMinPlayersPerTeam()) {
            return "Each team needs at least " + config.getMinPlayersPerTeam() + " player(s).";
        }

        // Load questions
        QuestionBank bank = QuestionBank.getInstance();
        String cat = category.equalsIgnoreCase("all") ? null : category;
        String diff = difficulty.equalsIgnoreCase("mixed") ? null : difficulty;
        questions = bank.getQuestions(cat, diff, numQuestions);

        if (questions.isEmpty()) {
            return "No questions available for the selected criteria.";
        }

        // Initialize player scores/results
        for (ClientHandler p : getAllPlayers()) {
            playerScores.put(p, 0);
            playerResults.put(p, new ArrayList<>());
        }

        state = RoomState.IN_PROGRESS;
        new Thread(this::runGame, "GameRoom-" + name).start();
        return null;
    }

    // --- Game loop (runs in its own thread) ---

    private void runGame() {
        try {
            broadcastToAll("\n============================================");
            broadcastToAll("       GAME STARTING IN 3 SECONDS...");
            broadcastToAll("  Category: " + category + " | Difficulty: " + difficulty);
            broadcastToAll("  Questions: " + questions.size());
            broadcastToAll("  " + team1.getName() + " vs " + team2.getName());
            broadcastToAll("============================================");
            Thread.sleep(3000);

            int timeLimit = GameConfig.getInstance().getQuestionTimeSeconds();

            for (int i = 0; i < questions.size(); i++) {
                if (getAllPlayers().isEmpty()) {
                    broadcastToAll("[GAME] All players left. Game ended.");
                    break;
                }

                Question q = questions.get(i);
                currentQuestionIndex = i;
                currentAnswers.clear();
                acceptingAnswers = true;

                broadcastToAll(q.getDisplayText(i + 1, questions.size()));
                broadcastToAll("Enter your answer (A/B/C/D). You have "
                               + timeLimit + " seconds:");

                // Timer countdown
                Set<Integer> warned = new HashSet<>();
                int[] warnings = {15, 10, 5, 3, 1};

                for (int remaining = timeLimit; remaining > 0; remaining--) {
                    if (allPlayersAnswered()) break;
                    Thread.sleep(1000);

                    for (int w : warnings) {
                        if (remaining - 1 == w && !warned.contains(w)) {
                            broadcastToAll(">> " + w + " seconds left!");
                            warned.add(w);
                        }
                    }
                }

                acceptingAnswers = false;

                // Evaluate answers
                evaluateQuestion(q);

                // Show results for this question
                broadcastToAll("\n--- Question Results ---");
                broadcastToAll("Correct answer: " + q.getCorrectChoiceText());
                broadcastToAll("");

                for (ClientHandler p : getAllPlayers()) {
                    String ans = currentAnswers.get(p);
                    String teamName = team1.hasPlayer(p) ? team1.getName() : team2.getName();
                    if (ans == null) {
                        broadcastToAll("  " + p.getUsername() + " (" + teamName
                                       + "): No answer (timeout)");
                    } else if (ans.equalsIgnoreCase(q.getCorrectAnswer())) {
                        broadcastToAll("  " + p.getUsername() + " (" + teamName
                                       + "): " + ans + " [CORRECT] +" + q.getPoints() + " pts");
                    } else {
                        broadcastToAll("  " + p.getUsername() + " (" + teamName
                                       + "): " + ans + " [WRONG]");
                    }
                }

                // Show team scoreboard
                broadcastToAll("\n--- Scoreboard ---");
                broadcastToAll("  " + team1.getName() + ": " + team1.getScore() + " pts");
                broadcastToAll("  " + team2.getName() + ": " + team2.getScore() + " pts");
                broadcastToAll("-------------------");

                if (i < questions.size() - 1) {
                    broadcastToAll("\nNext question in 3 seconds...");
                    Thread.sleep(3000);
                }
            }

            // Final results
            showFinalResults();

            // Save scores
            saveScores();

        } catch (InterruptedException e) {
            broadcastToAll("[GAME] Game interrupted.");
        } finally {
            state = RoomState.FINISHED;
            // Notify players that game ended so their input loops can exit
            broadcastToAll("\n[GAME] Game over! Returning to main menu...");
            RoomManager.getInstance().cleanup();
        }
    }

    private boolean allPlayersAnswered() {
        List<ClientHandler> all = getAllPlayers();
        for (ClientHandler p : all) {
            if (!currentAnswers.containsKey(p)) {
                return false;
            }
        }
        return !all.isEmpty();
    }

    private void evaluateQuestion(Question q) {
        for (ClientHandler p : getAllPlayers()) {
            String answer = currentAnswers.get(p);
            boolean correct = answer != null
                              && answer.equalsIgnoreCase(q.getCorrectAnswer());

            if (correct) {
                int points = q.getPoints();
                playerScores.merge(p, points, Integer::sum);

                // Add to team score
                if (team1.hasPlayer(p)) {
                    team1.addScore(points);
                } else if (team2 != null && team2.hasPlayer(p)) {
                    team2.addScore(points);
                }
            }

            // Track result
            String status = (answer == null) ? "timeout"
                            : (correct ? "correct" : "wrong");
            List<String[]> results = playerResults.get(p);
            if (results != null) {
                results.add(new String[]{
                    q.getText(),
                    answer != null ? answer : "-",
                    q.getCorrectAnswer(),
                    status
                });
            }
        }
    }

    private void showFinalResults() {
        broadcastToAll("\n\n================================================");
        broadcastToAll("              FINAL RESULTS");
        broadcastToAll("================================================");

        broadcastToAll("\n-- Team Scores --");
        broadcastToAll("  " + team1.getName() + ": " + team1.getScore() + " pts");
        if (team2 != null) {
            broadcastToAll("  " + team2.getName() + ": " + team2.getScore() + " pts");
        }

        if (team2 != null) {
            broadcastToAll("");
            if (team1.getScore() > team2.getScore()) {
                broadcastToAll("  >>> " + team1.getName() + " WINS! <<<");
            } else if (team2.getScore() > team1.getScore()) {
                broadcastToAll("  >>> " + team2.getName() + " WINS! <<<");
            } else {
                broadcastToAll("  >>> IT'S A TIE! <<<");
            }
        }

        broadcastToAll("\n-- Individual Scores --");
        List<Map.Entry<ClientHandler, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        int rank = 1;
        for (Map.Entry<ClientHandler, Integer> entry : sorted) {
            ClientHandler p = entry.getKey();
            String teamName = team1.hasPlayer(p) ? team1.getName()
                              : (team2 != null && team2.hasPlayer(p) ? team2.getName() : "N/A");
            broadcastToAll("  " + rank + ". " + p.getUsername()
                           + " (" + teamName + "): " + entry.getValue() + " pts");
            rank++;
        }

        // Per-player question details
        broadcastToAll("\n-- Question Details --");
        for (ClientHandler p : getAllPlayers()) {
            List<String[]> results = playerResults.get(p);
            if (results == null) continue;

            broadcastToAll("\n  " + p.getUsername() + ":");
            for (int i = 0; i < results.size(); i++) {
                String[] r = results.get(i);
                String statusTag;
                switch (r[3]) {
                    case "correct": statusTag = "[CORRECT]"; break;
                    case "wrong":   statusTag = "[WRONG]";   break;
                    default:        statusTag = "[TIMEOUT]"; break;
                }
                broadcastToAll("    Q" + (i + 1) + ": " + r[0]);
                broadcastToAll("         Your answer: " + r[1]
                               + " | Correct: " + r[2] + " " + statusTag);
            }
        }

        broadcastToAll("\n================================================");
    }

    private void saveScores() {
        ScoreManager sm = ScoreManager.getInstance();
        String date = LocalDate.now().toString();

        for (ClientHandler p : getAllPlayers()) {
            int score = playerScores.getOrDefault(p, 0);
            List<String[]> results = playerResults.get(p);
            int correctCount = 0;
            if (results != null) {
                for (String[] r : results) {
                    if ("correct".equals(r[3])) correctCount++;
                }
            }
            ScoreEntry entry = new ScoreEntry(
                p.getUsername(), date, "multiplayer",
                score, correctCount, questions.size(), name
            );
            sm.addScore(entry);
        }
    }

    // --- Answer submission ---

    public void submitAnswer(ClientHandler player, String answer) {
        if (!acceptingAnswers) {
            player.sendMessage("No active question right now.");
            return;
        }
        if (currentAnswers.containsKey(player)) {
            player.sendMessage("You already answered this question.");
            return;
        }
        String normalized = answer.trim().toUpperCase();
        if (!normalized.matches("[ABCD]")) {
            player.sendMessage("Invalid answer. Please enter A, B, C, or D.");
            return;
        }
        currentAnswers.put(player, normalized);
        player.sendMessage("Answer submitted: " + normalized);
    }

    // --- Broadcast ---

    public void broadcastToAll(String message) {
        for (ClientHandler p : getAllPlayers()) {
            p.sendMessage(message);
        }
    }

    // --- Status display ---

    public String getStatusDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- Room: ").append(name).append(" ---\n");
        sb.append("Category: ").append(category)
          .append(" | Difficulty: ").append(difficulty)
          .append(" | Questions: ").append(numQuestions).append("\n");
        sb.append("State: ").append(state).append("\n");

        sb.append("\nTeam 1 - ").append(team1.getName())
          .append(" (").append(team1.getPlayerCount()).append(" players):\n");
        for (ClientHandler p : team1.getPlayers()) {
            sb.append("  - ").append(p.getUsername()).append("\n");
        }

        if (team2 != null) {
            sb.append("Team 2 - ").append(team2.getName())
              .append(" (").append(team2.getPlayerCount()).append(" players):\n");
            for (ClientHandler p : team2.getPlayers()) {
                sb.append("  - ").append(p.getUsername()).append("\n");
            }
        } else {
            sb.append("Team 2 - [not formed yet]\n");
        }

        sb.append("---");
        return sb.toString();
    }

    public String getListDisplay() {
        String t2Info = (team2 != null)
            ? team2.getName() + " [" + team2.getPlayerCount() + "]"
            : "[empty]";
        return name + " (Creator: " + creator.getUsername()
               + ", " + team1.getName() + " [" + team1.getPlayerCount() + "] vs "
               + t2Info + ") [" + category + "/" + difficulty + "/" + numQuestions + "Q]";
    }
}
