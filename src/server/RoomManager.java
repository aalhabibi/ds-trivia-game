package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private static RoomManager instance;

    private RoomManager() {}

    public static synchronized RoomManager getInstance() {
        if (instance == null) {
            instance = new RoomManager();
        }
        return instance;
    }

    public synchronized String createRoom(String roomName, ClientHandler creator,
                                           String team1Name, String category,
                                           String difficulty, int numQuestions) {
        if (rooms.containsKey(roomName.toLowerCase())) {
            return "A room with that name already exists.";
        }
        GameRoom room = new GameRoom(roomName, creator, team1Name,
                                     category, difficulty, numQuestions);
        rooms.put(roomName.toLowerCase(), room);
        return null; // success
    }

    public GameRoom getRoom(String roomName) {
        return rooms.get(roomName.toLowerCase());
    }

    public synchronized void removeRoom(String roomName) {
        rooms.remove(roomName.toLowerCase());
    }

    public List<GameRoom> getWaitingRooms() {
        List<GameRoom> waiting = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            if (room.getState() == GameRoom.RoomState.WAITING) {
                waiting.add(room);
            }
        }
        return waiting;
    }

    /**
     * Clean up finished or empty rooms.
     */
    public synchronized void cleanup() {
        rooms.entrySet().removeIf(e ->
            e.getValue().getState() == GameRoom.RoomState.FINISHED);
    }
}
