package at.v3rtumnus.planman.util;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailUtilTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private MailUtil buildUtil(String adminEmail) {
        MailUtil util = new MailUtil();
        ReflectionTestUtils.setField(util, "mailSender", mailSender);
        ReflectionTestUtils.setField(util, "adminEmail", adminEmail);
        return util;
    }

    @Test
    void sendAdminMessage_withAdminEmail_sendsMessage() {
        MailUtil util = buildUtil("admin@example.com");

        util.sendAdminMessage("Test Subject", "Test Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).contains("admin@example.com");
        assertThat(sent.getSubject()).isEqualTo("Test Subject");
        assertThat(sent.getText()).isEqualTo("Test Body");
    }

    @Test
    void sendAdminMessage_withEmptyAdminEmail_doesNotSend() {
        MailUtil util = buildUtil("");

        util.sendAdminMessage("Subject", "Body");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
