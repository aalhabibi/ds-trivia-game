package model;

public class Question {
    private int id;
    private String category;
    private String difficulty;
    private String text;
    private String[] choices;
    private String correctAnswer; // A, B, C, or D

    public Question(int id, String category, String difficulty, String text,
                    String[] choices, String correctAnswer) {
        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.text = text;
        this.choices = choices;
        this.correctAnswer = correctAnswer.toUpperCase();
    }

    public int getId() { return id; }
    public String getCategory() { return category; }
    public String getDifficulty() { return difficulty; }
    public String getText() { return text; }
    public String[] getChoices() { return choices; }
    public String getCorrectAnswer() { return correctAnswer; }

    public int getPoints() {
        switch (difficulty.toLowerCase()) {
            case "easy":   return 10;
            case "medium": return 15;
            case "hard":   return 20;
            default:       return 10;
        }
    }

    public String getDisplayText(int questionNum, int totalQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================\n");
        sb.append("  Question ").append(questionNum).append(" of ").append(totalQuestions);
        sb.append("  |  Category: ").append(category);
        sb.append("  |  Difficulty: ").append(difficulty);
        sb.append("  |  Points: ").append(getPoints()).append("\n");
        sb.append("----------------------------------------\n");
        sb.append("  ").append(text).append("\n\n");
        sb.append("    A) ").append(choices[0]).append("\n");
        sb.append("    B) ").append(choices[1]).append("\n");
        sb.append("    C) ").append(choices[2]).append("\n");
        sb.append("    D) ").append(choices[3]).append("\n");
        sb.append("========================================");
        return sb.toString();
    }

    public String getCorrectChoiceText() {
        int idx = correctAnswer.charAt(0) - 'A';
        return correctAnswer + ") " + choices[idx];
    }

    public static Question fromFileString(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 8) return null;
        try {
            int id = Integer.parseInt(parts[0].trim());
            String category = parts[1].trim();
            String difficulty = parts[2].trim();
            String text = parts[3].trim();
            String[] choices = {
                parts[4].trim(), parts[5].trim(),
                parts[6].trim(), parts[7].trim()
            };
            // correct answer is extracted separately (9th field)
            return null; // handled below
        } catch (Exception e) {
            return null;
        }
    }

    public static Question fromFileStringFull(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 9) return null;
        try {
            int id = Integer.parseInt(parts[0].trim());
            String category = parts[1].trim();
            String difficulty = parts[2].trim();
            String text = parts[3].trim();
            String[] choices = {
                parts[4].trim(), parts[5].trim(),
                parts[6].trim(), parts[7].trim()
            };
            String answer = parts[8].trim().toUpperCase();
            return new Question(id, category, difficulty, text, choices, answer);
        } catch (Exception e) {
            return null;
        }
    }
}
