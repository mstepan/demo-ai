package com.github.mstepan.demo_ai.oci;

import com.oracle.bmc.generativeaiinference.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OCILogService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public void logLLMInteraction(OCIChatModel.RequestDirection direction, String logMsg) {
        if (LOGGER.isDebugEnabled()) {
            if (direction == OCIChatModel.RequestDirection.OUT_BOUND) {
                LOGGER.debug("APP ==========> LLM");
            } else {
                LOGGER.debug("APP <========== LLM");
            }

            LOGGER.debug("{}", logMsg);
        }
    }

    public Object buildLoggableRequest(ChatDetails chatDetails) {
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
        return switch (m) {
            case SystemMessage systemMessage -> "system";
            case UserMessage userMessage -> "user";
            case com.oracle.bmc.generativeaiinference.model.AssistantMessage assistantMessage ->
                    "assistant";
            default -> m.getClass().getSimpleName();
        };
    }
}
