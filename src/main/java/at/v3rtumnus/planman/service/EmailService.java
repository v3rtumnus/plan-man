package at.v3rtumnus.planman.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String SUBJECT_PREFIX = "Plan Man :: ";

    private final JavaMailSender emailSender;

    @Value("${admin.email}")
    private String adminEmail;

    public void sendSimpleMessage(String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(adminEmail);
        message.setTo(adminEmail);
        message.setSubject(SUBJECT_PREFIX + subject);
        message.setText(text);
        emailSender.send(message);
    }

    public void sendHtmlMessage(String subject, String text) throws MessagingException {
        final MimeMessage mimeMessage = emailSender.createMimeMessage();
        final MimeMessageHelper message =
                new MimeMessageHelper(mimeMessage, true, "UTF-8");
        message.setSubject(SUBJECT_PREFIX + subject);
        message.setFrom(adminEmail);
        message.setTo(adminEmail);
        message.setText(text, true);

        emailSender.send(mimeMessage);
    }
}
