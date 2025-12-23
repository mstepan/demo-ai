package com.github.mstepan.demo_ai.service;

import com.github.mstepan.demo_ai.oci.OCIChatModel;
import com.github.mstepan.demo_ai.web.Answer;
import com.github.mstepan.demo_ai.web.Question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandles;

@Service
public class ChatService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    //    private final ChatClient chatClient;
    //
    //    public ChatService(ChatClient.Builder chatClientBuilder) {
    //        this.chatClient = chatClientBuilder.build();
    //    }

    public Answer askQuestion(Question question) {
        //        var answerText = chatClient.prompt().user(question.question()).call().content();
        //        return new Answer(answerText);

        OCIChatModel model = new OCIChatModel();
        var answerText = model.call(question.question());
        return new Answer(answerText);
    }
}
