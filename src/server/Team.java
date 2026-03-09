package server;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Team {
    private final String name;
    private final List<ClientHandler> players = new CopyOnWriteArrayList<>();
    private int score;

    public Team(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public List<ClientHandler> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public int getPlayerCount() { return players.size(); }

    public synchronized int getScore() { return score; }

    public synchronized void addScore(int points) {
        this.score += points;
    }

    public void addPlayer(ClientHandler player) {
        players.add(player);
    }

    public void removePlayer(ClientHandler player) {
        players.remove(player);
    }

    public boolean hasPlayer(ClientHandler player) {
        return players.contains(player);
    }

    public void broadcastMessage(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    public synchronized void resetScore() {
        this.score = 0;
    }
}
