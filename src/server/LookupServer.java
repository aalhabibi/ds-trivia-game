package server;

import model.Question;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class LookupServer {

    private final int port;
    private final String questionsFilePath;
    private final List<Question> questions = new ArrayList<>();

    public LookupServer(int port, String questionsFilePath) {
        this.port = port;
        this.questionsFilePath = questionsFilePath;
    }

    /** Start the server. Blocks indefinitely (run in a daemon thread). */
    public void start() throws IOException {
        loadQuestions();
        System.out.println("[LOOKUP] LookupServer started on port " + port
                + " (" + questions.size() + " questions loaded).");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(new RequestHandler(client), "LookupHandler");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private void loadQuestions() throws IOException {
        File file = new File(questionsFilePath);
        if (!file.exists()) {
            System.err.println("[LOOKUP] Questions file not found: " + questionsFilePath);
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                Question q = Question.fromFileStringFull(line);
                if (q != null) {
                    questions.add(q);
                }

            }
        }
        System.out.println("[LOOKUP] Loaded " + questions.size() + " questions.");
    }

    private class RequestHandler implements Runnable {
        private final Socket socket;

        RequestHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String request = in.readLine();
                if (request == null) {
                    return;
                }
                request = request.trim();

                if (request.equals("CATEGORIES")) {
                    handleCategories(out);
                } else if (request.startsWith("COUNT|")) {
                    handleCount(request, out);
                } else if (request.startsWith("QUESTIONS|")) {
                    handleQuestions(request, out);
                }

            } catch (IOException e) {
                System.err.println("[LOOKUP] Handler error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) { /* ignore */ }
            }
        }

        private void handleCategories(PrintWriter out) {
            Set<String> cats = new TreeSet<>();
            for (Question q : questions) {
                cats.add(q.getCategory());
            }
            for (String cat : cats) {
                out.println(cat);
            }
            out.println("END");
        }

        private void handleCount(String request, PrintWriter out) {
            String[] parts = request.split("\\|", 3);
            if (parts.length < 3) {
                out.println("0");
                return;
            }
            String category  = "ALL".equals(parts[1])   ? null : parts[1];
            String difficulty = "MIXED".equals(parts[2]) ? null : parts[2];
            long count = questions.stream()
                    .filter(q -> category   == null || q.getCategory().equalsIgnoreCase(category))
                    .filter(q -> difficulty == null || q.getDifficulty().equalsIgnoreCase(difficulty))
                    .count();
            out.println(count);
        }

        private void handleQuestions(String request, PrintWriter out) {
            String[] parts = request.split("\\|", 4);
            if (parts.length < 4) {
                out.println("END");
                return;
            }
            String category  = "ALL".equals(parts[1])   ? null : parts[1];
            String difficulty = "MIXED".equals(parts[2]) ? null : parts[2];
            int count;
            try {
                count = Integer.parseInt(parts[3].trim());
            } catch (NumberFormatException e) {
                out.println("END");
                return;
            }

            List<Question> filtered = questions.stream()
                    .filter(q -> category   == null || q.getCategory().equalsIgnoreCase(category))
                    .filter(q -> difficulty == null || q.getDifficulty().equalsIgnoreCase(difficulty))
                    .collect(Collectors.toList());

            Collections.shuffle(filtered);
            List<Question> result = filtered.subList(0, Math.min(count, filtered.size()));

            for (Question q : result)
                out.println(toFileLine(q));
            out.println("END");
        }

        private String toFileLine(Question q) {
            return q.getId() + "|" + q.getCategory() + "|" + q.getDifficulty() + "|"
                    + q.getText() + "|"
                    + q.getChoices()[0] + "|" + q.getChoices()[1] + "|"
                    + q.getChoices()[2] + "|" + q.getChoices()[3] + "|"
                    + q.getCorrectAnswer();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 12346;
        String questionsFile = "data/questions.txt";

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) { /* use default */ }
        }
        if (args.length >= 2)
            questionsFile = args[1];

        new LookupServer(port, questionsFile).start();
    }
}
