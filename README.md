# Multiplayer Trivia Game — Distributed Systems Assignment 1

## Overview

A multiplayer trivia game built with Java Socket programming and multithreading. Supports single-player and team-based multiplayer modes over a client-server architecture.

## Requirements

- **Java JDK 8 or higher** (tested with JDK 17)
- No external libraries — uses only standard Java APIs (`java.net`, `java.io`, `java.util`)

## Project Structure

```
Assignment 1/
├── src/
│   ├── model/               # Data model classes
│   │   ├── User.java        # User account model
│   │   ├── Question.java    # Trivia question model
│   │   └── ScoreEntry.java  # Score history entry model
│   ├── server/              # Server-side code
│   │   ├── GameServer.java      # Main server entry point
│   │   ├── ClientHandler.java   # Per-client connection handler (threaded)
│   │   ├── UserManager.java     # User authentication & registration
│   │   ├── QuestionBank.java    # Question loading & filtering
│   │   ├── ScoreManager.java    # Score history persistence
│   │   ├── GameConfig.java      # Configuration loader
│   │   ├── GameRoom.java        # Multiplayer game room & game loop
│   │   ├── RoomManager.java     # Active rooms registry
│   │   └── Team.java            # Team within a game room
│   └── client/              # Client-side code
│       └── GameClient.java  # Thin terminal client (2 threads)
├── data/                    # Data files (loaded at startup)
│   ├── config.properties    # Server configuration
│   ├── users.txt            # User credentials
│   ├── scores.txt           # Score history
│   └── questions.txt        # Question bank (37 MCQs)
├── compile.bat              # Compilation script
├── run_server.bat           # Launch server
├── run_client.bat           # Launch client
└── README.md                # This file
```

## How to Compile & Run

### Step 1: Compile

```
compile.bat
```

Or manually:

```
javac -d bin src\model\*.java src\server\*.java src\client\*.java
```

### Step 2: Start the Server

```
run_server.bat
```

Or manually:

```
java -cp bin server.GameServer
```

The server loads all data files from the `data/` directory and listens on port **12345** (configurable in `data/config.properties`).

### Step 3: Start Client(s)

Open one or more new terminal windows and run:

```
run_client.bat
```

Or manually:

```
java -cp bin client.GameClient localhost 12345
```

You can connect multiple clients simultaneously (at least 4 supported).

## Game Features

### Authentication

- **Login**: Enter existing username and password
- **Register**: Create a new account with name, username, and password
- Error codes:
  - **401** — Wrong password (Unauthorized)
  - **404** — Username not found
  - **409** — Username already taken during registration

### Single Player Mode

1. Choose a question category (Geography, Science, Math, History, Technology, or All)
2. Choose difficulty (Easy, Medium, Hard, or Mixed)
3. Choose number of questions
4. Answer timed MCQ questions (A/B/C/D)
5. See results and score saved to history

### Multiplayer Mode (Teams)

1. **Create Room**: Set room name, team name, category, difficulty, and question count
2. **Join Room**: Browse available rooms, join an existing team or create Team 2
3. Room creator types `start` when both teams have equal players
4. Questions are broadcast to all players; each player answers individually
5. Team scores are the sum of individual member scores
6. After the game: full results with per-player question details

### Game Mechanics

- Each question has a time limit (default: 30 seconds, configurable)
- Timer warnings at 15, 10, 5, 3, and 1 second(s) remaining
- Each player gets ONE attempt per question
- Answers are case-insensitive (a = A)
- Late answers after timeout are rejected
- Type `-` at any time to quit the current game/menu

### Scoring

| Difficulty | Points per Correct Answer |
| ---------- | ------------------------- |
| Easy       | 10                        |
| Medium     | 15                        |
| Hard       | 20                        |

Wrong answers and timeouts score 0 points. No negative scoring.

### Score History

View your past game scores from the main menu. History is persisted across server restarts.

## Configuration

Edit `data/config.properties` to customize:

```properties
server.port=12345
min.players.per.team=1
max.players.per.team=4
question.time.seconds=30
```

## Data File Formats

All data files use `|` as a delimiter.

### users.txt

```
# name|username|password
John Doe|john|pass123
```

### questions.txt

```
# id|category|difficulty|question|choiceA|choiceB|choiceC|choiceD|answer
1|Geography|easy|What is the capital of France?|London|Paris|Berlin|Madrid|B
```

### scores.txt

```
# username|date|mode|score|correct|total|roomName
john|2026-03-10|single|120|8|10|
```

## Pre-loaded Test Accounts

| Name        | Username | Password |
| ----------- | -------- | -------- |
| John Doe    | john     | pass123  |
| Jane Smith  | jane     | pass456  |
| Alice Brown | alice    | pass789  |
| Bob Wilson  | bob      | pass000  |

## Design Decisions & Assumptions

1. **Architecture**: One thread per client (ClientHandler). Game rooms run their game loop in a separate thread. The client is a thin relay with two threads (server reader + stdin sender).

2. **Protocol**: Plain text line-based protocol. The server sends display lines; the client prints them. The client sends user input lines. No binary framing or JSON — keeps the implementation simple and debuggable.

3. **Thread Safety**: ConcurrentHashMap for shared data structures. Synchronized methods on UserManager and ScoreManager for file I/O. Socket timeout-based polling (1-2s) for non-blocking lobby/game loops.

4. **Disconnection Handling**: When a client disconnects, the ClientHandler's cleanup removes them from any active room. The game continues with remaining players. If all players leave, the game ends.

5. **Team Formation**: Room creator sets game parameters. Other players join and pick a team. Team 2 must be created by the first player to join it (they choose the name). The game requires equal team sizes to start.

6. **Password Storage**: Stored in plain text for simplicity. In a production system, passwords should be hashed with bcrypt/scrypt.

7. **Questions**: 37 MCQ questions across 5 categories (Geography, Science, Math, History, Technology) with 3 difficulty levels. Questions are randomly shuffled per game.

8. **Data Persistence**: User registrations and scores are saved immediately to disk. The question bank and config are read-only at startup.

9. **Error Handling**: Invalid inputs show descriptive error messages. Malformed data file lines are silently skipped. Socket errors trigger graceful disconnection and cleanup.

10. **Quit Mechanism**: Typing `-` at any prompt allows the user to go back or quit the game. This works consistently throughout all menus and during gameplay.
