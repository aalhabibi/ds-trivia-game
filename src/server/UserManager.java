package server;

import model.User;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Set<String> loggedInUsers = ConcurrentHashMap.newKeySet();
    private final String filePath;
    private static UserManager instance;

    private UserManager(String filePath) {
        this.filePath = filePath;
    }

    public static synchronized UserManager getInstance(String filePath) {
        if (instance == null) {
            instance = new UserManager(filePath);
        }
        return instance;
    }

    public static synchronized UserManager getInstance() {
        return instance;
    }

    public void load() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
            System.out.println("[USERS] Created empty users file.");
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                User user = User.fromFileString(line);
                if (user != null) {
                    users.put(user.getUsername(), user);
                }
            }
        }

        // Ensure admin exists
        boolean hasAdmin = users.values().stream().anyMatch(User::isAdmin);
        if (!hasAdmin) {
            User admin = new User("Admin", "admin", "admin123", true, 0);
            users.put(admin.getUsername(), admin);
            save();
            System.out.println("[USERS] No admin found, created default admin user.");
        }

        System.out.println("[USERS] Loaded " + users.size() + " users.");
    }

    public synchronized void save() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("# name|username|password|isAdmin|wins");
            for (User user : users.values()) {
                pw.println(user.toFileString());
            }
        }
    }

    /**
     * Authenticate a user.
     * 
     * @return the User if successful
     * @throws UserNotFoundException if username not found (404)
     * @throws UnauthorizedException if password is wrong (401)
     */
    public synchronized User authenticate(String username, String password)
            throws UserNotFoundException, UnauthorizedException, AlreadyLoggedInException {
        User user = users.get(username);
        if (user == null) {
            throw new UserNotFoundException("Username '" + username + "' not found.");
        }
        if (!user.getPassword().equals(password)) {
            throw new UnauthorizedException("Wrong password.");
        }
        if (loggedInUsers.contains(username)) {
            throw new AlreadyLoggedInException("User is already logged in from another session.");
        }
        loggedInUsers.add(username);
        return user;
    }

    public synchronized void logout(String username) {
        if (username != null && !username.trim().isEmpty()) {
            loggedInUsers.remove(username);
        }
    }

    public int getLoggedInCount() {
        return loggedInUsers.size();
    }

    public synchronized void recordWin(String username) {
        User user = users.get(username);
        if (user != null) {
            user.addWin();
            try {
                save();
            } catch (IOException e) {
                System.err.println("[USERS] Failed to save after recording win.");
            }
        }
    }

    public List<User> getUsersWithMostWins() {
        List<User> bestUsers = new ArrayList<>();
        int maxWins = 0;

        for (User u : users.values()) {
            if (u.isAdmin()) continue;

            if (u.getWins() > maxWins) {
                maxWins = u.getWins();
                bestUsers.clear();
                bestUsers.add(u);
            } else if (u.getWins() == maxWins && maxWins > 0) {
                bestUsers.add(u);
            }
        }

        return bestUsers;
    }

    /**
     * Register a new user.
     * 
     * @return the newly created User
     * @throws UsernameExistsException if username is already taken
     */
    public synchronized User register(String name, String username, String password)
            throws UsernameExistsException {
        if (users.containsKey(username)) {
            throw new UsernameExistsException(
                    "Username '" + username + "' is already taken.");
        }
        User user = new User(name, username, password);
        users.put(username, user);
        loggedInUsers.add(username);
        try {
            save();
        } catch (IOException e) {
            System.err.println("[USERS] Failed to save after registration: " + e.getMessage());
        }
        return user;
    }

    // Custom exceptions
    public static class UserNotFoundException extends Exception {
        public UserNotFoundException(String msg) {
            super(msg);
        }
    }

    public static class UnauthorizedException extends Exception {
        public UnauthorizedException(String msg) {
            super(msg);
        }
    }

    public static class UsernameExistsException extends Exception {
        public UsernameExistsException(String msg) {
            super(msg);
        }
    }

    public static class AlreadyLoggedInException extends Exception {
        public AlreadyLoggedInException(String msg) {
            super(msg);
        }
    }
}
