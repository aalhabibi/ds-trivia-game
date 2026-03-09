package model;

public class ScoreEntry {
    private String username;
    private String date;
    private String mode;       // "single" or "multiplayer"
    private int score;
    private int correct;
    private int total;
    private String roomName;   // empty for single player

    public ScoreEntry(String username, String date, String mode,
                      int score, int correct, int total, String roomName) {
        this.username = username;
        this.date = date;
        this.mode = mode;
        this.score = score;
        this.correct = correct;
        this.total = total;
        this.roomName = roomName;
    }

    public String getUsername() { return username; }
    public String getDate() { return date; }
    public String getMode() { return mode; }
    public int getScore() { return score; }
    public int getCorrect() { return correct; }
    public int getTotal() { return total; }
    public String getRoomName() { return roomName; }

    public String toFileString() {
        return username + "|" + date + "|" + mode + "|" + score + "|"
               + correct + "|" + total + "|" + (roomName != null ? roomName : "");
    }

    public static ScoreEntry fromFileString(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 7) return null;
        try {
            return new ScoreEntry(
                parts[0].trim(),
                parts[1].trim(),
                parts[2].trim(),
                Integer.parseInt(parts[3].trim()),
                Integer.parseInt(parts[4].trim()),
                Integer.parseInt(parts[5].trim()),
                parts[6].trim()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public String getDisplayString() {
        String modeStr = mode.equals("single") ? "Single Player" : "Multiplayer";
        String roomStr = (roomName != null && !roomName.isEmpty()) ? " (Room: " + roomName + ")" : "";
        return String.format("[%s] %s%s - Score: %d (%d/%d correct)",
                date, modeStr, roomStr, score, correct, total);
    }
}
