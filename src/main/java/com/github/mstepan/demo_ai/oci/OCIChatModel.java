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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

public class OCIChatModel implements ChatModel {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // https://docs.oracle.com/en-us/iaas/Content/generative-ai/pretrained-models.htm
    // google.gemini-2.5-pro <-- not-working (in beta)
    // xai.grok-4
    // meta.llama-3.2-90b-vision-instruct
    // meta.llama-4-scout-17b-16e-instruct
    // meta.llama-4-maverick-17b-128e-instruct-fp8 <-- best so far
    private static final String LLM_MODEL_ID = "meta.llama-4-maverick-17b-128e-instruct-fp8";

    private static final int SOCKET_CONNECTION_TIMEOUT_IN_MS = 10_000;
    private static final int SOCKET_READ_TIMEOUT_IN_MS = 60_000;

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
                            .compartmentId(
                                    "ocid1.compartment.oc1..aaaaaaaadwjibfornz4simrjcqftsoxvnyn5syxqklv76e5rjmbucvkbvuwa")
                            .servingMode(
                                    OnDemandServingMode.builder().modelId(LLM_MODEL_ID).build())
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

            ChatChoice firstChoice = choices.get(0);

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
            var authProvider = new SessionTokenAuthenticationDetailsProvider("bmc_operator_access");

            var clientConfig =
                    ClientConfiguration.builder()
                            .connectionTimeoutMillis(SOCKET_CONNECTION_TIMEOUT_IN_MS)
                            .readTimeoutMillis(SOCKET_READ_TIMEOUT_IN_MS)
                            .build();

            var client =
                    GenerativeAiInferenceClient.builder()
                            .configuration(clientConfig)
                            .build(authProvider);
            client.setRegion(Region.US_CHICAGO_1);
            return client;
        } catch (IOException ioEx) {
            throw new IllegalStateException(ioEx);
        }
    }
}
