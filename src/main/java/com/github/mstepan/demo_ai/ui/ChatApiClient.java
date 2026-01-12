package com.github.mstepan.demo_ai.ui;

import com.github.mstepan.demo_ai.domain.Answer;
import com.github.mstepan.demo_ai.domain.Question;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ChatApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient.Builder webClientBuilder;

    public ChatApiClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<String> ask(String baseUrl, String prompt) {
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        return client.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new Question(prompt))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> new ChatApiException(friendlyMessage(resp.statusCode(), body), resp.statusCode().value()))
                )
                .bodyToMono(Answer.class)
                .map(Answer::answer);
    }

    public Flux<String> askStream(String baseUrl, String prompt) {
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        return client.post()
                .uri("/ask/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue(new Question(prompt))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> new ChatApiException(friendlyMessage(resp.statusCode(), body), resp.statusCode().value()))
                )
                // Each NDJSON line is a JSON string; map to String chunks
                .bodyToFlux(String.class)
                .map(ChatApiClient::decodeJsonString);
    }

    private static String decodeJsonString(String json) {
        try {
            return MAPPER.readValue(json, String.class);
        } catch (Exception e) {
            return json;
        }
    }

    private static String friendlyMessage(HttpStatusCode status, String body) {
        String snippet = body == null ? "" : body.replaceAll("\n", " ").trim();
        if (snippet.length() > 200) {
            snippet = snippet.substring(0, 200) + "...";
        }
        if (status.is4xxClientError()) {
            return "Validation error (" + status.value() + ")" + (snippet.isEmpty() ? "" : ": " + snippet);
        }
        if (status.is5xxServerError()) {
            return "Server error (" + status.value() + ") — please retry." + (snippet.isEmpty() ? "" : " Details: " + snippet);
        }
        return "HTTP " + status.value() + (snippet.isEmpty() ? "" : ": " + snippet);
    }
}
