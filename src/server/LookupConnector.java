package server;

import model.Question;
import java.io.*;
import java.net.*;
import java.util.*;

public class LookupConnector {

    private static String host = "localhost";
    private static int port = 12346;

    public static void configure(String host, int port) {
        LookupConnector.host = host;
        LookupConnector.port = port;
    }

    /** Returns all available question categories. */
    public static Set<String> getCategories() {
        Set<String> cats = new TreeSet<>();
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("CATEGORIES");
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line))
                    break;
                cats.add(line);
            }
        } catch (IOException e) {
            System.err.println("[LOOKUP-CLIENT] getCategories failed: " + e.getMessage());
        }
        return cats;
    }

    /**
     * Returns the number of questions matching the given criteria.
     * Pass {@code null} for category to mean "all categories",
     * {@code null} for difficulty to mean "mixed/all difficulties".
     */
    public static int getAvailableCount(String category, String difficulty) {
        String cat  = (category   == null) ? "ALL"   : category;
        String diff = (difficulty == null) ? "MIXED" : difficulty;
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("COUNT|" + cat + "|" + diff);
            String line = in.readLine();
            if (line != null) {
                return Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[LOOKUP-CLIENT] getAvailableCount failed: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns up to {@code count} shuffled questions matching the criteria.
     * Pass {@code null} for category/difficulty to mean "all".
     */
    public static List<Question> getQuestions(String category, String difficulty, int count) {
        String cat  = (category   == null) ? "ALL"   : category;
        String diff = (difficulty == null) ? "MIXED" : difficulty;
        List<Question> result = new ArrayList<>();
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("QUESTIONS|" + cat + "|" + diff + "|" + count);
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line))
                    break;
                Question q = Question.fromFileStringFull(line);
                if (q != null)
                    result.add(q);
            }
        } catch (IOException e) {
            System.err.println("[LOOKUP-CLIENT] getQuestions failed: " + e.getMessage());
        }
        return result;
    }
}
