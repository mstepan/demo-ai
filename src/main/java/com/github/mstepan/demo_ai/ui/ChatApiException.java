package com.github.mstepan.demo_ai.ui;

/**
 * User-facing exception for API errors coming from the backend calls used by the Vaadin UI.
 */
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
