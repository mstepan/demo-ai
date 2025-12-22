package com.github.mstepan.demo_ai.service;

import com.github.mstepan.demo_ai.web.Answer;
import com.github.mstepan.demo_ai.web.Question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public Answer askQuestion(Question question) {

        var answerText = chatClient.prompt().user(question.question()).call().content();
        return new Answer(answerText);

        //        try {
        //            return new Answer(askOciLlm());
        //        } catch (IOException ioEx) {
        //            LOGGER.error(ioEx.getMessage(), ioEx);
        //        }

        //        return new Answer("undefined");
    }

    //    private static String askOciLlm() throws IOException {
    //
    //        var CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".oci",
    // "config").toString();
    //
    //        LOGGER.info("Configuring OCI LLM, oci config file {}", CONFIG_FILE);
    //
    //        var COMPARTMENT_ID =
    //                System.getenv(
    //
    // "ocid1.tenancy.oc1..aaaaaaaaqkcilbifc6tm4wlnefa2ofmazjqgdhodaqtsgrnrbbenkahkf62a");
    //        var MODEL_ID = System.getenv("meta.llama-4-maverick-17b-128e-instruct-fp8");
    //
    //        ConfigFileAuthenticationDetailsProvider authProvider =
    //                new ConfigFileAuthenticationDetailsProvider(CONFIG_FILE,
    // "bmc_operator_access");
    //        var genAi =
    //                GenerativeAiInferenceClient.builder()
    //                        .region(Region.valueOf("us-chicago-1"))
    //                        .build(authProvider);
    //
    //        var chatModel =
    //                new OCICohereChatModel(
    //                        genAi,
    //                        OCICohereChatOptions.builder()
    //                                .model(MODEL_ID)
    //                                .compartment(COMPARTMENT_ID)
    //                                .servingMode("on-demand")
    //                                .build());
    //
    //        ChatResponse response =
    //                chatModel.call(new Prompt("Generate the names of 5 famous pirates."));
    //
    //        return response.getResult().getOutput().getText();
    //    }
}
