package io.github.vssavin.umlib.service;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vssavin on 12.07.2022.
 */
@Service
@Primary
public class MockedEmailServiceImpl implements MockedEmailService{

    private final List<EmailMessage> messages = new ArrayList<>();

    @Override
    public void sendSimpleMessage(String destinationEmail, String subject, String text) {
        messages.add(new EmailMessage(destinationEmail, subject, text));
    }

    @Override
    public List<EmailMessage> getEmailMessages() {
        return messages;
    }

    @Override
    public EmailMessage getLastEmailMessage() {
        if (messages.size() == 0) throw new IllegalStateException("Emails list is empty!");
        return messages.get(messages.size() - 1);
    }
}
