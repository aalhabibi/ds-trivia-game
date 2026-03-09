package server;

import model.Question;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class QuestionBank {
    private final List<Question> questions = new ArrayList<>();
    private final String filePath;
    private static QuestionBank instance;

    private QuestionBank(String filePath) {
        this.filePath = filePath;
    }

    public static synchronized QuestionBank getInstance(String filePath) {
        if (instance == null) {
            instance = new QuestionBank(filePath);
        }
        return instance;
    }

    public static synchronized QuestionBank getInstance() {
        return instance;
    }

    public void load() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("[QUESTIONS] Questions file not found: " + filePath);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Question q = Question.fromFileStringFull(line);
                if (q != null) {
                    questions.add(q);
                }
            }
        }
        System.out.println("[QUESTIONS] Loaded " + questions.size() + " questions.");
    }

    /**
     * Get a filtered and randomized list of questions.
     * @param category null for all categories
     * @param difficulty null for mixed difficulty
     * @param count max number of questions
     */
    public List<Question> getQuestions(String category, String difficulty, int count) {
        List<Question> filtered = questions.stream()
            .filter(q -> category == null || q.getCategory().equalsIgnoreCase(category))
            .filter(q -> difficulty == null || q.getDifficulty().equalsIgnoreCase(difficulty))
            .collect(Collectors.toList());

        Collections.shuffle(filtered);
        return filtered.subList(0, Math.min(count, filtered.size()));
    }

    public Set<String> getCategories() {
        Set<String> cats = new TreeSet<>();
        for (Question q : questions) {
            cats.add(q.getCategory());
        }
        return cats;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    /**
     * Get count of questions matching the given criteria.
     */
    public int getAvailableCount(String category, String difficulty) {
        return (int) questions.stream()
            .filter(q -> category == null || q.getCategory().equalsIgnoreCase(category))
            .filter(q -> difficulty == null || q.getDifficulty().equalsIgnoreCase(difficulty))
            .count();
    }
}
