package com.luxpretty.app.mail.app;

import com.postmarkapp.postmark.Postmark;
import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "postmark")
public class PostmarkMailSender implements MailSender {

    private final ApiClient client;
    private final String fromAddress;
    private final String fromName;

    public PostmarkMailSender(
            @Value("${app.mail.postmark.api-token}") String apiToken,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name:LuxPretty}") String fromName
    ) {
        this.client = Postmark.getApiClient(apiToken);
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String send(String recipientEmail, String subject, String htmlBody, String textBody) {
        Message msg = new Message(
                fromName + " <" + fromAddress + ">",
                recipientEmail,
                subject,
                textBody
        );
        msg.setHtmlBody(htmlBody);
        msg.setMessageStream("outbound");

        try {
            MessageResponse resp = client.deliverMessage(msg);
            return resp.getMessageId();
        } catch (PostmarkException e) {
            String msgText = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (msgText.contains("inactiverecipient")
                    || msgText.contains("invalidemail")
                    || msgText.contains("422")
                    || msgText.contains("401")) {
                throw new HardMailException("Postmark hard error: " + e.getMessage(), e);
            }
            throw new RetryableMailException("Postmark transient error: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RetryableMailException("Postmark IO error", e);
        }
    }
}
