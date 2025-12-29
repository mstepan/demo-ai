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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Primary
@Component
public class OCIChatModel implements ChatModel {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
                                .writeValueAsString(buildLoggableRequest(chatDetails));
                logLLMInteraction(RequestDirection.OUT_BOUND, rawJson);
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

                    logLLMInteraction(RequestDirection.IN_BOUND, rawJson);
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

    enum RequestDirection {
        OUT_BOUND,
        IN_BOUND
    }

    private static void logLLMInteraction(RequestDirection direction, String logMsg) {
        if (LOGGER.isDebugEnabled()) {
            if (direction == RequestDirection.OUT_BOUND) {
                LOGGER.debug("APP ==========> LLM");
            } else {
                LOGGER.debug("APP <========== LLM");
            }

            LOGGER.debug("{}", logMsg);
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

    private static Object buildLoggableRequest(ChatDetails chatDetails) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (chatDetails == null) {
            return root;
        }

        root.put("compartmentId", chatDetails.getCompartmentId());

        Map<String, Object> serving = new LinkedHashMap<>();
        if (chatDetails.getServingMode() instanceof OnDemandServingMode sm) {
            serving.put("modelId", sm.getModelId());
        } else if (chatDetails.getServingMode() != null) {
            serving.put("servingModeType", chatDetails.getServingMode().getClass().getSimpleName());
        } else {
            serving.put("servingModeType", null);
        }
        root.put("servingMode", serving);

        Map<String, Object> req = new LinkedHashMap<>();
        if (chatDetails.getChatRequest() instanceof GenericChatRequest gcr) {
            List<Object> messages = new ArrayList<>();
            if (gcr.getMessages() != null) {
                for (var m : gcr.getMessages()) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    String role = getRole(m);
                    mm.put("role", role);

                    List<Object> contentList = new ArrayList<>();
                    if (m.getContent() != null) {
                        for (var c : m.getContent()) {
                            Map<String, Object> cc = new LinkedHashMap<>();
                            if (c instanceof TextContent tc) {
                                cc.put("type", "text");
                                cc.put("text", tc.getText());
                            } else if (c instanceof ImageContent ic) {
                                cc.put("type", "image");
                                cc.put("url", ic.getImageUrl());
                            } else {
                                cc.put("type", c.getClass().getSimpleName());
                            }
                            contentList.add(cc);
                        }
                    }
                    mm.put("content", contentList);
                    messages.add(mm);
                }
            }
            req.put("messages", messages);
            req.put("isStream", gcr.getIsStream());
            req.put("numGenerations", gcr.getNumGenerations());
            req.put("seed", gcr.getSeed());
            req.put("isEcho", gcr.getIsEcho());
            req.put("topK", gcr.getTopK());
            req.put("topP", gcr.getTopP());
            req.put("temperature", gcr.getTemperature());
            req.put("frequencyPenalty", gcr.getFrequencyPenalty());
            req.put("presencePenalty", gcr.getPresencePenalty());
            req.put("stop", gcr.getStop());
            req.put("logProbs", gcr.getLogProbs());
            req.put("maxTokens", gcr.getMaxTokens());
            req.put("logitBias", gcr.getLogitBias());
            req.put("toolChoice", gcr.getToolChoice());
            req.put("tools", gcr.getTools());
        }
        root.put("chatRequest", req);
        return root;
    }

    private static String getRole(Message m) {
        String role;
        if (m instanceof SystemMessage) {
            role = "system";
        } else if (m instanceof UserMessage) {
            role = "user";
        } else if (m instanceof com.oracle.bmc.generativeaiinference.model.AssistantMessage) {
            role = "assistant";
        } else {
            role = m.getClass().getSimpleName();
        }
        return role;
    }
}
