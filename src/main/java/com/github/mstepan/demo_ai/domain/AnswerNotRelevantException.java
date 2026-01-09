package com.github.mstepan.demo_ai.domain;

public final class AnswerNotRelevantException extends RuntimeException {

    private final String question;
    private final String answer;

    public AnswerNotRelevantException(String question, String answer) {
        super(String.format("The answer '%s' is not relevant to question '%s'", answer, question));
        this.question = question;
        this.answer = answer;
    }

    public String question() {
        return question;
    }

    public String answer() {
        return answer;
    }
}
