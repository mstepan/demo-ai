package com.github.mstepan.demo_ai.ui;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractor;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

class ChatApiClientTest {

    private static WebClient.Builder builderFor(ExchangeFunction fn) {
        return WebClient.builder().exchangeFunction(fn);
    }

    private static DataBuffer buf(String s) {
        return DefaultDataBufferFactory.sharedInstance.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void ask_nonStreaming_success() {
        ExchangeFunction fn = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"answer\":\"Hello\"}")
                        .build()
        );

        ChatApiClient client = new ChatApiClient(builderFor(fn));

        StepVerifier.create(client.ask("http://localhost:0", "hi"))
                .expectNext("Hello")
                .verifyComplete();
    }

    @Test
    void ask_nonStreaming_validation_error_maps_to_exception() {
        ExchangeFunction fn = req -> Mono.just(
                ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/problem+json")
                        .body("{\"title\":\"Bad Request\"}")
                        .build()
        );

        ChatApiClient client = new ChatApiClient(builderFor(fn));

        StepVerifier.create(client.ask("http://localhost:0", ""))
                .expectErrorSatisfies(err -> {
                    assert err instanceof ChatApiException;
                    ChatApiException ex = (ChatApiException) err;
                    assert ex.getStatus() == 400;
                })
                .verify();
    }

    @Test
    void ask_streaming_success_emits_chunks() {
        Flux<DataBuffer> ndjson = Flux.just("\"Hel\"\n", "\"lo\"\n")
                .map(ChatApiClientTest::buf);

        ExchangeFunction fn = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/x-ndjson")
                        .body(ndjson)
                        .build()
        );

        ChatApiClient client = new ChatApiClient(builderFor(fn));

        StepVerifier.create(client.askStream("http://localhost:0", "hi"))
                .expectNext("Hel")
                .expectNext("lo")
                .verifyComplete();
    }
}
