package com.github.mstepan.demo_ai.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Objects;

@CssImport("./styles/chat-view.css")
@Route("")
public class ChatView extends VerticalLayout {

    private final ChatApiClient api;

    private final Div messages;
    private final Scroller scroller;
    private final TextArea input;
    private final Checkbox streamingToggle;
    private final Button sendBtn;
    private final Button cancelBtn;
    private final Button clearBtn;

    private volatile Disposable inFlightStream;

    @Value("${server.port:7171}")
    private int serverPort;

    public ChatView(ChatApiClient api) {
        this.api = api;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        messages = new Div();
        messages.getStyle().set("display", "flex");
        messages.getStyle().set("flexDirection", "column");
        messages.getStyle().set("gap", "0.5rem");
        messages.setWidthFull();

        scroller = new Scroller(messages);
        scroller.setSizeFull();

        input = new TextArea();
        input.setLabel("Message");
        input.getElement().setAttribute("aria-label", "Message input");
        input.setWidthFull();
        input.setPlaceholder("Ask something...");
        input.setMaxLength(8000);
        input.setAutofocus(true);
        input.setClearButtonVisible(true);

        streamingToggle = new Checkbox("Streaming", true);
        streamingToggle.getElement().setAttribute("aria-label", "Toggle streaming mode");
        sendBtn = new Button("Send", e -> sendMessage());
        sendBtn.getElement().setAttribute("aria-label", "Send message");
        cancelBtn = new Button("Cancel", e -> cancelStreaming());
        cancelBtn.getElement().setAttribute("aria-label", "Cancel streaming response");
        cancelBtn.setEnabled(false);
        clearBtn = new Button("Clear", e -> clearConversation());
        clearBtn.getElement().setAttribute("aria-label", "Clear conversation");

        // Keyboard: Enter to send (Shift+Enter = newline)
        Shortcuts.addShortcutListener(this, this::sendMessage, Key.ENTER)
                .listenOn(input);

        HorizontalLayout controls = new HorizontalLayout(streamingToggle, sendBtn, cancelBtn, clearBtn);
        controls.addClassName("controls");
        controls.setWidthFull();
        controls.setAlignItems(Alignment.END);

        add(scroller, input, controls);
        expand(scroller);
    }

    private void sendMessage() {
        String text = input.getValue() != null ? input.getValue().trim() : "";
        if (text.isBlank()) {
            Notification.show("Please enter a message.", 2500, Notification.Position.MIDDLE);
            return;
        }

        // Append user bubble
        messages.add(bubble(text, true));
        autoScroll();

        input.clear();
        input.focus();

        final String baseUrl = currentOrigin();

        if (Boolean.TRUE.equals(streamingToggle.getValue())) {
            // Prepare assistant bubble to update incrementally
            Div assistant = bubble("", false);
            messages.add(assistant);
            autoScroll();

            cancelBtn.setEnabled(true);
            sendBtn.setEnabled(false);
            streamingToggle.setEnabled(false);

            Flux<String> stream = api.askStream(baseUrl, text);
            inFlightStream = stream.subscribe(
                    chunk -> getUI().ifPresent(ui -> ui.access(() -> {
                        assistant.setText(assistant.getText() + chunk);
                        autoScroll();
                    })),
                    err -> getUI().ifPresent(ui -> ui.access(() -> {
                        Notification.show("Streaming failed: " + safeMsg(err), 3000, Notification.Position.MIDDLE);
                        cancelBtn.setEnabled(false);
                        sendBtn.setEnabled(true);
                        streamingToggle.setEnabled(true);
                    })),
                    () -> getUI().ifPresent(ui -> ui.access(() -> {
                        cancelBtn.setEnabled(false);
                        sendBtn.setEnabled(true);
                        streamingToggle.setEnabled(true);
                        autoScroll();
                    }))
            );
        } else {
            sendBtn.setEnabled(false);
            streamingToggle.setEnabled(false);
            api.ask(baseUrl, text)
               .subscribe(answer -> getUI().ifPresent(ui -> ui.access(() -> {
                           messages.add(bubble(answer, false));
                           sendBtn.setEnabled(true);
                           streamingToggle.setEnabled(true);
                           autoScroll();
                           input.focus();
                       })),
                       err -> getUI().ifPresent(ui -> ui.access(() -> {
                           Notification.show("Request failed: " + safeMsg(err), 3000, Notification.Position.MIDDLE);
                           sendBtn.setEnabled(true);
                           streamingToggle.setEnabled(true);
                           input.focus();
                       }))
               );
        }
    }

    private void cancelStreaming() {
        Disposable d = inFlightStream;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
        cancelBtn.setEnabled(false);
        sendBtn.setEnabled(true);
        streamingToggle.setEnabled(true);
        input.focus();
    }

    private void clearConversation() {
        messages.removeAll();
        input.focus();
    }

    private Div bubble(String text, boolean user) {
        Div wrapper = new Div();
        wrapper.getStyle().set("display", "flex");
        wrapper.getStyle().set("width", "100%");
        wrapper.getStyle().set("justifyContent", user ? "flex-end" : "flex-start");

        Div bubble = new Div();
        bubble.setText(text);
        bubble.getStyle().set("maxWidth", "72ch");
        bubble.getStyle().set("whiteSpace", "pre-wrap");
        bubble.getStyle().set("padding", "0.5rem 0.75rem");
        bubble.getStyle().set("borderRadius", "12px");
        bubble.getStyle().set("backgroundColor", user ? "var(--lumo-primary-color-10pct)" : "var(--lumo-contrast-10pct)");
        bubble.getStyle().set("border", user ? "1px solid var(--lumo-primary-color)" : "1px solid var(--lumo-contrast-30pct)");

        wrapper.add(bubble);
        return wrapper;
    }

    private void autoScroll() {
        scroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    private String currentOrigin() {
        // UI and backend live in the same Spring Boot app; call localhost using configured server.port
        return "http://localhost:" + serverPort;
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getSimpleName();
    }
}
