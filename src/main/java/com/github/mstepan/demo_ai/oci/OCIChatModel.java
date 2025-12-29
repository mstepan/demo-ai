package com.github.mstepan.demo_ai.oci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.*;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.lang.invoke.MethodHandles;
import java.util.List;

@Primary
@Component
public class OCIChatModel implements ChatModel, StreamingChatModel {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final GenAiClientFactoryFactory clientConfig;
    private final OCILogService logService;
    private final OCIGenAiProperties properties;
    private final OCIGenAiStreamingSinkFactory streamingSinkFactory;

    public OCIChatModel(
            GenAiClientFactoryFactory clientConfig,
            OCILogService logService,
            OCIGenAiProperties properties,
            OCIGenAiStreamingSinkFactory streamingSinkFactory) {
        this.clientConfig = clientConfig;
        this.logService = logService;
        this.properties = properties;
        this.streamingSinkFactory = streamingSinkFactory;
    }

    @Override
    @NonNull
    public ChatResponse call(@NonNull Prompt prompt) {
        try (GenerativeAiInferenceClient client = clientConfig.newClient()) {

            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/SystemMessage
            SystemMessage systemPrompt =
                    SystemMessage.builder()
                            .content(
                                    List.of(
                                            TextContent.builder()
                                                    .text(prompt.getSystemMessage().getText())
                                                    .build()))
                            .build();

            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/TextContent
            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/ImageContent
            UserMessage userQuery =
                    UserMessage.builder()
                            .content(
                                    List.of(
                                            TextContent.builder()
                                                    .text(prompt.getUserMessage().getText())
                                                    .build()))
                            .build();

            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/GenericChatRequest
            GenericChatRequest genericChatRequest =
                    GenericChatRequest.builder()
                            .messages(List.of(systemPrompt, userQuery))
                            .temperature(
                                    properties
                                            .temperature()) // configured via oci.genai.temperature
                            .maxTokens(properties.maxTokens())
                            .isEcho(false)
                            .isStream(false)
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
                                .writeValueAsString(logService.buildLoggableRequest(chatDetails));
                logService.logLLMInteraction(RequestDirection.OUT_BOUND, rawJson);
            }

            // Send request to the LLM
            var genericResponse = client.chat(chatRequest);

            if (genericResponse
                    instanceof
                    com.oracle.bmc.generativeaiinference.responses.ChatResponse response) {

                if (LOGGER.isDebugEnabled()) {
                    String rawJson =
                            JSON_MAPPER
                                    .writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(response.getChatResult());

                    logService.logLLMInteraction(RequestDirection.IN_BOUND, rawJson);
                }

                if (response.getChatResult().getChatResponse()
                        instanceof GenericChatResponse baseResponse) {

                    List<ChatChoice> choices = baseResponse.getChoices();

                    if (choices.isEmpty()) {
                        LOGGER.warn("No choices inside LLM response");
                        return ChatResponse.builder().build();
                    }

                    ChatChoice firstChoice = choices.getFirst();

                    List<ChatContent> chatContent = firstChoice.getMessage().getContent();

                    if (chatContent.isEmpty()) {
                        System.err.println("Chat content is empty");
                        return ChatResponse.builder().build();
                    }

                    if (chatContent.getFirst() instanceof TextContent textContent) {
                        AssistantMessage assistant = new AssistantMessage(textContent.getText());
                        return ChatResponse.builder()
                                .generations(List.of(new Generation(assistant)))
                                .build();
                    } else {
                        LOGGER.warn("ChatContent is not of type TextContent");
                        return ChatResponse.builder().build();
                    }
                } else {
                    LOGGER.warn(
                            "'response.getChatResult().getChatResponse()' is not of type 'GenericChatResponse'");
                    return ChatResponse.builder().build();
                }
            } else {
                LOGGER.warn(
                        "'genericResponse' is not of type 'com.oracle.bmc.generativeaiinference.responses.ChatResponse'");
                return ChatResponse.builder().build();
            }

        } catch (Exception ex) {
            LOGGER.error("OCI GenAI call failed", ex);
            return ChatResponse.builder().build();
        }
    }

    @Override
    @NonNull
    public Flux<ChatResponse> stream(@NonNull Prompt prompt) {
        return Flux.<ChatResponse>create(streamingSinkFactory.newInstance(prompt))
                .subscribeOn(Schedulers.boundedElastic());
    }

    enum RequestDirection {
        OUT_BOUND,
        IN_BOUND
    }
}
