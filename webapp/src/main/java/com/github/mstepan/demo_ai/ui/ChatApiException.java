package com.github.mstepan.demo_ai.ui;

public class ChatApiException extends RuntimeException {
    private final int status;

    public ChatApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
