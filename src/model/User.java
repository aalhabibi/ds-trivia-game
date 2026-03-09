package model;

public class User {
    private String name;
    private String username;
    private String password;

    public User(String name, String username, String password) {
        this.name = name;
        this.username = username;
        this.password = password;
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

    public String toFileString() {
        return name + "|" + username + "|" + password;
    }

    public static User fromFileString(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 3)
            return null;
        return new User(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }
}
