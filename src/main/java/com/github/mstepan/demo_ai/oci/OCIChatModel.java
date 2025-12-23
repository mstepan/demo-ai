package com.github.mstepan.demo_ai.oci;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
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

    private final OCIGenAiProperties properties;
    private static final int DEFAULT_CONNECTION_TIMEOUT_SEC = 10;
    private static final int DEFAULT_READ_TIMEOUT_SEC = 60;

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

            LOGGER.debug("SYSTEM PROMPT: {}", systemPrompt.toString());

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

            LOGGER.debug("USER PROMPT: {}", userQuery.toString());

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
            com.oracle.bmc.generativeaiinference.responses.ChatResponse response =
                    client.chat(chatRequest);

            BaseChatResponse baseResponse = response.getChatResult().getChatResponse();

            List<ChatChoice> choices = ((GenericChatResponse) baseResponse).getChoices();

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
        } catch (Exception ex) {
            LOGGER.error("OCI GenAI call failed", ex);
            return ChatResponse.builder().build();
        }
    }

    private GenerativeAiInferenceClient newClient() {
        try {
            var authProvider =
                    new SessionTokenAuthenticationDetailsProvider(properties.profile());

            int connectionTimeoutSec =
                    (properties.connectionTimeout() != null)
                            ? properties.connectionTimeout()
                            : DEFAULT_CONNECTION_TIMEOUT_SEC;

            int readTimeoutSec =
                    (properties.readTimeout() != null)
                            ? properties.readTimeout()
                            : DEFAULT_READ_TIMEOUT_SEC;

            var clientConfig =
                    ClientConfiguration.builder()
                            .connectionTimeoutMillis(connectionTimeoutSec * 1000)
                            .readTimeoutMillis(readTimeoutSec * 1000)
                            .build();

            var client =
                    GenerativeAiInferenceClient.builder()
                            .configuration(clientConfig)
                            .build(authProvider);
            client.setRegion(Region.fromRegionId(properties.region()));
            return client;
        } catch (IOException ioEx) {
            throw new IllegalStateException(ioEx);
        }
    }
}
