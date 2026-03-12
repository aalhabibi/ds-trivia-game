package server;

import model.Question;
import model.ScoreEntry;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PublicGameRoom {
    public enum State {
        WAITING, IN_PROGRESS, FINISHED
    }

    private static final int COUNTDOWN_SECONDS = 10;

    private final int minPlayers;
    private final int maxPlayers;
    private final int numQuestions;

    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();
    private volatile State state = State.WAITING;
    private volatile boolean countdownRunning = false;

    // Game state
    private List<Question> questions;
    private volatile boolean acceptingAnswers = false;
    private final Map<ClientHandler, String>       currentAnswers = new ConcurrentHashMap<>();
    private final Map<ClientHandler, Integer>      playerScores   = new ConcurrentHashMap<>();
    private final Map<ClientHandler, List<String[]>> playerResults = new ConcurrentHashMap<>();

    public PublicGameRoom(int minPlayers, int maxPlayers, int numQuestions) {
        this.minPlayers   = minPlayers;
        this.maxPlayers   = maxPlayers;
        this.numQuestions = numQuestions;
    }

    // Join / leave

    public State getState() { return state; }

    public synchronized boolean canJoin() {
        return state == State.WAITING && players.size() < maxPlayers;
    }

    /**
     * Add a player to this room.
     * Returns an error string on failure, or {@code null} on success.
     * When minPlayers is reached a 10-second countdown begins.
     * Reaching maxPlayers starts the game immediately.
     */
    public synchronized String addPlayer(ClientHandler player) {
        if (state != State.WAITING)
            return "Game already started.";
        if (players.size() >= maxPlayers)
            return "Public room is full.";

        players.add(player);
        broadcastToAll("[PUBLIC ROOM] " + player.getUsername() + " joined. (" + players.size() + "/" + maxPlayers + " players.");

        if (players.size() >= maxPlayers) {
            state = State.IN_PROGRESS;
            broadcastToAll("[PUBLIC ROOM] Room is full! Starting now...");
            new Thread(this::runGame, "PublicRoom-Game").start();

        } else if (players.size() >= minPlayers && !countdownRunning) {
            countdownRunning = true;
            new Thread(this::runCountdown, "PublicRoom-Countdown").start();

        }
        return null;
    }

    public synchronized void removePlayer(ClientHandler player) {
        if (state == State.WAITING) {
            players.remove(player);
            broadcastToAll("[PUBLIC ROOM] " + player.getUsername()
                    + " left the queue. (" + players.size() + "/" + maxPlayers + ")");

            // stop countdown if not enough players
            if (players.size() < minPlayers) {
                countdownRunning = false;
                broadcastToAll("[PUBLIC ROOM] Not enough players. Countdown cancelled.");
            }

        } else {
            // Game in progress — just notify; don't remove from list so scorecard stays intact
            broadcastToAll("[PUBLIC ROOM] " + player.getUsername() + " disconnected.");
        }
    }

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

    // Game loop

    private void runCountdown() {
        broadcastToAll("\n[PUBLIC ROOM] Minimum players reached! Game starting in " + COUNTDOWN_SECONDS + " seconds.");

        for (int remaining = COUNTDOWN_SECONDS; remaining > 0; remaining--) {
            if (!countdownRunning || players.size() < minPlayers) {
                broadcastToAll("[PUBLIC ROOM] Countdown stopped. Waiting for players...");
                return;
            }

            if (state == State.IN_PROGRESS) {
                return;
            }

            if (remaining <= 5 || remaining == COUNTDOWN_SECONDS) {
                broadcastToAll("[PUBLIC ROOM] Starting in " + remaining + " second"
                        + (remaining == 1 ? "" : "s") + "... ("
                        + players.size() + "/" + maxPlayers + " players)");
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // countdown finished — launch if not already started
        synchronized (this) {
            if (state != State.WAITING) {
                return;
            }
            state = State.IN_PROGRESS;
        }
        broadcastToAll("[PUBLIC ROOM] Countdown over! Starting with " + players.size() + " player(s).");
        runGame();
    }

    private void runGame() {
        // Fetch questions from LookupServer (all categories, mixed difficulty)
        questions = LookupConnector.getQuestions(null, null, numQuestions);

        if (questions.isEmpty()) {
            broadcastToAll("[PUBLIC ROOM] No questions available. Game cancelled.");
            state = State.FINISHED;
            RoomManager.getInstance().clearPublicRoom(this);
            return;
        }

        for (ClientHandler p : players) {
            playerScores.put(p, 0);
            playerResults.put(p, new ArrayList<>());
        }

        broadcastToAll("\n============================================");
        broadcastToAll("       PUBLIC GAME STARTING IN 3 SECONDS!");
        broadcastToAll("  Players: " + players.size()
                + " | Questions: " + questions.size()
                + " | Category: All | Difficulty: Mixed");
        broadcastToAll("============================================");

        try { Thread.sleep(3000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int timeLimit = GameConfig.getInstance().getQuestionTimeSeconds();

        for (int i = 0; i < questions.size(); i++) {
            if (players.isEmpty()) {
                broadcastToAll("[GAME] All players left. Game ended.");
                break;
            }

            Question q = questions.get(i);
            currentAnswers.clear();
            acceptingAnswers = true;

            broadcastToAll(q.getDisplayText(i + 1, questions.size()));
            broadcastToAll("Enter your answer (A/B/C/D). You have " + timeLimit + " seconds:");

            // Timer countdown
            Set<Integer> warned = new HashSet<>();
            int[] warnings = { 15, 10, 5, 3, 1 };
            for (int remaining = timeLimit; remaining > 0; remaining--) {
                if (allPlayersAnswered()) break;
                try { Thread.sleep(1000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int w : warnings) {
                    if (remaining - 1 == w && !warned.contains(w)) {
                        broadcastToAll(">> " + w + " seconds left!");
                        warned.add(w);
                    }
                }
            }
            acceptingAnswers = false;

            // Evaluate answers
            for (ClientHandler p : players) {
                String answer  = currentAnswers.get(p);
                boolean correct = answer != null
                        && answer.equalsIgnoreCase(q.getCorrectAnswer());
                if (correct)
                    playerScores.merge(p, q.getPoints(), Integer::sum);

                String status = (answer == null) ? "timeout"
                        : (correct ? "correct" : "wrong");
                List<String[]> results = playerResults.get(p);
                if (results != null)
                    results.add(new String[]{
                            q.getText(),
                            answer != null ? answer : "-",
                            q.getCorrectAnswer(),
                            status
                    });
            }

            // Show question results
            broadcastToAll("\n--- Question Results ---");
            broadcastToAll("Correct answer: " + q.getCorrectChoiceText());
            for (ClientHandler p : players) {
                String ans = currentAnswers.get(p);
                if (ans == null) {
                    broadcastToAll("  " + p.getUsername() + ": No answer (timeout)");
                } else if (ans.equalsIgnoreCase(q.getCorrectAnswer())) {
                    broadcastToAll("  " + p.getUsername()
                            + ": " + ans + " [CORRECT] +" + q.getPoints() + " pts");
                } else {
                    broadcastToAll("  " + p.getUsername() + ": " + ans + " [WRONG]");
                }
            }

            // Live scoreboard
            broadcastToAll("\n--- Scoreboard ---");
            List<Map.Entry<ClientHandler, Integer>> ranks =
                    new ArrayList<>(playerScores.entrySet());
            ranks.sort((a, b) -> b.getValue() - a.getValue());
            for (int r = 0; r < ranks.size(); r++) {
                broadcastToAll("  " + (r + 1) + ". "
                        + ranks.get(r).getKey().getUsername()
                        + ": " + ranks.get(r).getValue() + " pts");
            }
            broadcastToAll("---");

            if (i < questions.size() - 1) {
                broadcastToAll("\nNext question in 3 seconds...");
                try { Thread.sleep(3000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        showFinalResults();
        saveScores();
        state = State.FINISHED;
        broadcastToAll("\n[GAME] Game over! Returning to main menu...");
        RoomManager.getInstance().clearPublicRoom(this);
    }

    private boolean allPlayersAnswered() {
        for (ClientHandler p : players) {
            if (!currentAnswers.containsKey(p)) return false;
        }
        return !players.isEmpty();
    }

    private void showFinalResults() {
        broadcastToAll("\n\n================================================");
        broadcastToAll("         FINAL RESULTS - PUBLIC GAME");
        broadcastToAll("================================================");

        List<Map.Entry<ClientHandler, Integer>> sorted = new ArrayList<>(playerScores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        broadcastToAll("\n-- Final Rankings --");
        for (int i = 0; i < sorted.size(); i++) {
            ClientHandler p = sorted.get(i).getKey();
            broadcastToAll("  " + (i + 1) + ". "
                    + p.getUsername() + ": " + sorted.get(i).getValue() + " pts");
        }

        if (!sorted.isEmpty()) {
            int topScore = sorted.getFirst().getValue();
            List<String> winners = new ArrayList<>();
            for (Map.Entry<ClientHandler, Integer> e : sorted) {
                if (e.getValue() == topScore)
                    winners.add(e.getKey().getUsername());
            }
            if (winners.size() == 1) {
                broadcastToAll("\n  >>> " + winners.getFirst() + " WINS! <<<");
            } else {
                broadcastToAll("\n  >>> TIE between: " + String.join(", ", winners) + " <<<");
            }
        }
        broadcastToAll("\n================================================");
    }

    private void saveScores() {
        ScoreManager sm = ScoreManager.getInstance();
        String date = LocalDate.now().toString();

        // Determine the highest score for win recording
        int maxScore = playerScores.values().stream()
                .mapToInt(Integer::intValue).max().orElse(-1);

        for (ClientHandler p : players) {
            int score = playerScores.getOrDefault(p, 0);
            List<String[]> results = playerResults.getOrDefault(p, Collections.emptyList());
            int correctCount = (int) results.stream()
                    .filter(r -> "correct".equals(r[3])).count();

            ScoreEntry entry = new ScoreEntry(
                    p.getUsername(), date, "multiplayer",
                    score, correctCount, questions.size(), "public");
            sm.addScore(entry);

            // Record a win for the highest scorer(s)
            if (maxScore > 0 && score == maxScore) {
                UserManager.getInstance().recordWin(p.getUsername());
            }
        }
    }

    // Broadcast

    public void broadcastToAll(String message) {
        for (ClientHandler p : players) {
            p.sendMessage(message);
        }
    }
}
