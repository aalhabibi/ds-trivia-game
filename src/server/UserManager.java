package server;

import model.User;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();
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
            return;
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
        System.out.println("[USERS] Loaded " + users.size() + " users.");
    }

    public synchronized void save() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("# name|username|password");
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
    public User authenticate(String username, String password)
            throws UserNotFoundException, UnauthorizedException {
        User user = users.get(username);
        if (user == null) {
            throw new UserNotFoundException("Username '" + username + "' not found.");
        }
        if (!user.getPassword().equals(password)) {
            throw new UnauthorizedException("Wrong password.");
        }
        return user;
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
}
