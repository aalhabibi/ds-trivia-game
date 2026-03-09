package server;

import model.ScoreEntry;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreManager {
    private final Map<String, List<ScoreEntry>> scoreHistory = new ConcurrentHashMap<>();
    private final String filePath;
    private static ScoreManager instance;

    private ScoreManager(String filePath) {
        this.filePath = filePath;
    }

    public static synchronized ScoreManager getInstance(String filePath) {
        if (instance == null) {
            instance = new ScoreManager(filePath);
        }
        return instance;
    }

    public static synchronized ScoreManager getInstance() {
        return instance;
    }

    public void load() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
            System.out.println("[SCORES] Created empty scores file.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                ScoreEntry entry = ScoreEntry.fromFileString(line);
                if (entry != null) {
                    scoreHistory.computeIfAbsent(entry.getUsername(), k -> new ArrayList<>())
                            .add(entry);
                }
            }
        }

        int total = scoreHistory.values().stream().mapToInt(List::size).sum();
        System.out.println("[SCORES] Loaded " + total + " score entries for "
                + scoreHistory.size() + " users.");
    }

    public synchronized void addScore(ScoreEntry entry) {
        scoreHistory.computeIfAbsent(entry.getUsername(), k -> new ArrayList<>())
                .add(entry);
        try {
            save();
        } catch (IOException e) {
            System.err.println("[SCORES] Failed to save scores: " + e.getMessage());
        }
    }

    public List<ScoreEntry> getHistory(String username) {
        return scoreHistory.getOrDefault(username, Collections.emptyList());
    }

    private void save() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("# username|date|mode|score|correct|total|roomName");
            for (List<ScoreEntry> entries : scoreHistory.values()) {
                for (ScoreEntry entry : entries) {
                    pw.println(entry.toFileString());
                }
            }
        }
    }
}
