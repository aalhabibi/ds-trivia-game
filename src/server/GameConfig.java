package server;

import java.io.*;
import java.util.Properties;

public class GameConfig {
    private int serverPort;
    private int lookupServerPort;
    private int minPlayersPerTeam;
    private int maxPlayersPerTeam;
    private int questionTimeSeconds;
    private int publicRoomMinPlayers;
    private int publicRoomMaxPlayers;
    private int publicRoomNumQuestions;

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
        lookupServerPort = Integer.parseInt(props.getProperty("lookup.server.port", "12346"));
        minPlayersPerTeam = Integer.parseInt(props.getProperty("min.players.per.team", "1"));
        maxPlayersPerTeam = Integer.parseInt(props.getProperty("max.players.per.team", "4"));
        questionTimeSeconds = Integer.parseInt(props.getProperty("question.time.seconds", "30"));
        publicRoomMinPlayers = Integer.parseInt(props.getProperty("public.room.min.players", "2"));
        publicRoomMaxPlayers = Integer.parseInt(props.getProperty("public.room.max.players", "6"));
        publicRoomNumQuestions = Integer.parseInt(props.getProperty("public.room.num.questions", "10"));

        System.out.println("[CONFIG] Loaded configuration:");
        System.out.println("  Port: " + serverPort);
        System.out.println("  Lookup port: " + lookupServerPort);
        System.out.println("  Min players/team: " + minPlayersPerTeam);
        System.out.println("  Max players/team: " + maxPlayersPerTeam);
        System.out.println("  Question time: " + questionTimeSeconds + "s");
        System.out.println("  Public room: min=" + publicRoomMinPlayers + " max=" + publicRoomMaxPlayers + " questions=" + publicRoomNumQuestions);
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getLookupServerPort() {
        return lookupServerPort;
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

    public int getPublicRoomMinPlayers() {
        return publicRoomMinPlayers;
    }

    public int getPublicRoomMaxPlayers() {
        return publicRoomMaxPlayers;
    }

    public int getPublicRoomNumQuestions() {
        return publicRoomNumQuestions;
    }
}
