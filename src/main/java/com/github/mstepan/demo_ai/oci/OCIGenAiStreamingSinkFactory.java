package com.github.mstepan.demo_ai.oci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.*;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Configuration
public class OCIGenAiStreamingSinkFactory {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final GenAiClientFactoryFactory clientConfig;
    private final OCILogService logService;
    private final OCIGenAiProperties properties;

    public OCIGenAiStreamingSinkFactory(
            GenAiClientFactoryFactory clientConfig,
            OCILogService logService,
            OCIGenAiProperties properties) {
        this.clientConfig = clientConfig;
        this.logService = logService;
        this.properties = properties;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Consumer<FluxSink<ChatResponse>> newInstance(Prompt prompt) {
        return new OCIStreamingConsumer(prompt);
    }

    private final class OCIStreamingConsumer implements Consumer<FluxSink<ChatResponse>> {

        private final Prompt prompt;

        public OCIStreamingConsumer(Prompt prompt) {
            this.prompt = prompt;
        }

        @Override
        public void accept(FluxSink<ChatResponse> sink) {

            try (GenerativeAiInferenceClient client = clientConfig.newClient()) {
                // Build messages (system + user) same as non-streaming
                SystemMessage systemPrompt =
                        SystemMessage.builder()
                                .content(
                                        List.of(
                                                TextContent.builder()
                                                        .text(prompt.getSystemMessage().getText())
                                                        .build()))
                                .build();

                UserMessage userQuery =
                        UserMessage.builder()
                                .content(
                                        List.of(
                                                TextContent.builder()
                                                        .text(prompt.getUserMessage().getText())
                                                        .build()))
                                .build();

                // Enable streaming
                GenericChatRequest genericChatRequest =
                        GenericChatRequest.builder()
                                .messages(List.of(systemPrompt, userQuery))
                                .temperature(properties.temperature())
                                .isEcho(false)
                                .isStream(true)
                                .build();

                ChatDetails chatDetails =
                        ChatDetails.builder()
                                .compartmentId(properties.compartment())
                                .servingMode(
                                        OnDemandServingMode.builder()
                                                .modelId(properties.model())
                                                .build())
                                .chatRequest(genericChatRequest)
                                .build();

                ChatRequest chatRequest = ChatRequest.builder().chatDetails(chatDetails).build();

                if (LOGGER.isDebugEnabled()) {
                    String rawJson =
                            JSON_MAPPER
                                    .writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(
                                            logService.buildLoggableRequest(chatDetails));
                    logService.logLLMInteraction(OCIChatModel.RequestDirection.OUT_BOUND, rawJson);
                }

                // Call the API with streaming enabled
                Object responseObj = client.chat(chatRequest);

                InputStream is = eventStream(responseObj);

                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        // SSE lines may include comments or blank lines. We only
                        // care about 'data:' lines.
                        if (line.isEmpty() || line.startsWith(":")) {
                            continue;
                        }

                        if (line.startsWith("data:")) {
                            String data = line.substring("data:".length()).trim();
                            if ("[DONE]".equalsIgnoreCase(data)) {
                                break; // end of stream
                            }

                            try {
                                // Each event is a JSON object; parse it
                                // structurally.
                                @SuppressWarnings("unchecked")
                                Map<String, Object> event = JSON_MAPPER.readValue(data, Map.class);

                                // Extract text delta from the event payload, robust
                                // to schema variations.
                                String delta = extractTextDelta(event);
                                if (delta != null && !delta.isEmpty()) {
                                    org.springframework.ai.chat.messages.AssistantMessage
                                            assistantMsg =
                                                    new org.springframework.ai.chat.messages
                                                            .AssistantMessage(delta);
                                    ChatResponse cr =
                                            ChatResponse.builder()
                                                    .generations(
                                                            List.of(new Generation(assistantMsg)))
                                                    .build();
                                    sink.next(cr);
                                }
                            } catch (Exception parseEx) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(
                                            "Failed to parse streaming event line: {}",
                                            line,
                                            parseEx);
                                }
                                // Ignore malformed lines but continue the stream.
                            }
                        }
                    }

                    // When the stream ends, complete.
                    sink.complete();
                }
            } catch (Exception ex) {
                LOGGER.error("OCI GenAI streaming call failed", ex);
                sink.error(ex);
            }
        }
    }

    private static InputStream eventStream(Object responseObj) {
        if (responseObj
                instanceof
                com.oracle.bmc.generativeaiinference.responses.ChatResponse chatResponse) {
            return chatResponse.getEventStream();
        }

        throw new IllegalStateException(
                "responseObj is not of type com.oracle.bmc.generativeaiinference.responses.ChatResponse");
    }

    /**
     * Extract text delta from a streaming event JSON. This method is tolerant to schema variations
     * by scanning for the first reachable "text" string value.
     */
    private static String extractTextDelta(Map<String, Object> event) {
        Object res = findFirstText(event);
        return res instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Object findFirstText(Object node) {
        if (node instanceof Map) {
            for (var entry : ((Map<String, Object>) node).entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if ("text".equalsIgnoreCase(key) && val instanceof String) {
                    return val;
                }
                Object nested = findFirstText(val);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) {
                Object nested = findFirstText(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}
