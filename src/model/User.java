package model;

public class User {
    private String name;
    private String username;
    private String password;
    private boolean isAdmin;
    private int wins;

    public User(String name, String username, String password) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.isAdmin = false;
        this.wins = 0;
    }

    public User(String name, String username, String password, boolean isAdmin, int wins) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.isAdmin  = isAdmin;
        this.wins = wins;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        wins++;
    }

    public String toFileString() {
        return name + "|" + username + "|" + password + "|" + isAdmin + "|" + wins;
    }

    public static User fromFileString(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 3)
            return null;

        boolean isAdmin = parts.length > 3 && "true".equalsIgnoreCase(parts[3].trim());
        int wins = parts.length > 4 ? Integer.parseInt(parts[4].trim()) : 0;

        return new User(parts[0].trim(), parts[1].trim(), parts[2].trim(), isAdmin, wins);
    }
}
