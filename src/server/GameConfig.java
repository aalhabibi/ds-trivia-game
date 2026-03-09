package server;

import java.io.*;
import java.util.Properties;

public class GameConfig {
    private int serverPort;
    private int minPlayersPerTeam;
    private int maxPlayersPerTeam;
    private int questionTimeSeconds;

    private static GameConfig instance;

    private GameConfig() {
    }

    public static synchronized GameConfig getInstance() {
        if (instance == null) {
            instance = new GameConfig();
        }
        return instance;
    }

    public void load(String filePath) throws IOException {
        Properties props = new Properties();
        File file = new File(filePath);

        if (!file.exists()) {
            // Create default config
            props.setProperty("server.port", "12345");
            props.setProperty("min.players.per.team", "1");
            props.setProperty("max.players.per.team", "4");
            props.setProperty("question.time.seconds", "30");
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "Trivia Game Configuration");
            }
        }

        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        }

        serverPort = Integer.parseInt(props.getProperty("server.port", "12345"));
        minPlayersPerTeam = Integer.parseInt(props.getProperty("min.players.per.team", "1"));
        maxPlayersPerTeam = Integer.parseInt(props.getProperty("max.players.per.team", "4"));
        questionTimeSeconds = Integer.parseInt(props.getProperty("question.time.seconds", "30"));

        System.out.println("[CONFIG] Loaded configuration:");
        System.out.println("  Port: " + serverPort);
        System.out.println("  Min players/team: " + minPlayersPerTeam);
        System.out.println("  Max players/team: " + maxPlayersPerTeam);
        System.out.println("  Question time: " + questionTimeSeconds + "s");
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getMinPlayersPerTeam() {
        return minPlayersPerTeam;
    }

    public int getMaxPlayersPerTeam() {
        return maxPlayersPerTeam;
    }

    public int getQuestionTimeSeconds() {
        return questionTimeSeconds;
    }
}
