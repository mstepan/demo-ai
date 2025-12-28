package com.github.mstepan.demo_ai.oci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.*;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

@Primary
@Component
public class OCIChatModel implements ChatModel {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OCIGenAiProperties properties;
    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;

    public OCIChatModel(OCIGenAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try (GenerativeAiInferenceClient client = newClient()) {

            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/SystemMessage
            SystemMessage systemPrompt =
                    SystemMessage.builder()
                            .content(
                                    List.of(
                                            TextContent.builder()
                                                    .text(prompt.getSystemMessage().getText())
                                                    .build()))
                            .build();

            logLLMInteraction("SYSTEM PROMPT", prompt.getSystemMessage().getText());

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

            logLLMInteraction("USER PROMPT", prompt.getUserMessage().getText());

            // https://docs.oracle.com/en-us/iaas/api/#/en/generative-ai-inference/20231130/datatypes/GenericChatRequest
            GenericChatRequest genericChatRequest =
                    GenericChatRequest.builder()
                            .messages(List.of(systemPrompt, userQuery))
                            // .maxTokens(1028) use max model context
                            .temperature(0.0) // Deterministic output
                            // .topP(1.0) // No nucleus sampling needed with temp=0
                            .topK(1) // Use full vocabulary (fine)
                            // .frequencyPenalty(0.0) // No penalty for repeating words
                            // .presencePenalty(0.0) // No penalty for repeating ideas
                            .isEcho(false)
                            .isStream(false)
                            .build();

            ChatDetails chatDetails =
                    ChatDetails.builder()
                            // ugbuocinative/CEGBU-Textura
                            .compartmentId(properties.compartment())
                            .servingMode(
                                    OnDemandServingMode.builder()
                                            .modelId(properties.model())
                                            .build())
                            .chatRequest(genericChatRequest)
                            .build();

            ChatRequest chatRequest = ChatRequest.builder().chatDetails(chatDetails).build();

            /* Send request to the Client */
            var genericResponse = client.chat(chatRequest);

            if (genericResponse
                    instanceof
                    com.oracle.bmc.generativeaiinference.responses.ChatResponse response) {

                if (LOGGER.isDebugEnabled()) {
                    String rawJson =
                            JSON.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(response.getChatResult());

                    logLLMInteraction("RAW OCI GEN AI RESPONSE", rawJson);
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

    private static void logLLMInteraction(String title, String logMsg) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "======================================= {} =======================================",
                    title);
            LOGGER.debug("{}", logMsg);
            LOGGER.debug(
                    "=======================================================================================================");
        }
    }

    private GenerativeAiInferenceClient newClient() {
        try {
            var authProvider = new SessionTokenAuthenticationDetailsProvider(properties.profile());

            int connectionTimeoutMs =
                    (properties.connectionTimeout() != null)
                            ? (int) properties.connectionTimeout().toMillis()
                            : DEFAULT_CONNECTION_TIMEOUT_MS;

            int readTimeoutMs =
                    (properties.readTimeout() != null)
                            ? (int) properties.readTimeout().toMillis()
                            : DEFAULT_READ_TIMEOUT_MS;

            var clientConfig =
                    ClientConfiguration.builder()
                            .connectionTimeoutMillis(connectionTimeoutMs)
                            .readTimeoutMillis(readTimeoutMs)
                            .build();

            var client =
                    GenerativeAiInferenceClient.builder()
                            .configuration(clientConfig)
                            .build(authProvider);
            //
            // TODO: we can also use region insteadof or URL below
            //
            //            client.setRegion(Region.fromRegionId(properties.region()));
            client.setEndpoint(properties.baseUrl());
            return client;
        } catch (IOException ioEx) {
            throw new IllegalStateException(ioEx);
        }
    }
}
