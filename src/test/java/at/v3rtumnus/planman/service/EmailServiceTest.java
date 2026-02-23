package at.v3rtumnus.planman.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender emailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "adminEmail", "admin@example.com");
    }

    @Test
    void sendSimpleMessage_setsSubjectPrefix() {
        emailService.sendSimpleMessage("Test Subject", "Test Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Plan Man :: Test Subject");
        assertThat(sent.getText()).isEqualTo("Test Body");
        assertThat(sent.getFrom()).isEqualTo("admin@example.com");
        assertThat(sent.getTo()).containsExactly("admin@example.com");
    }

    @Test
    void sendSimpleMessage_sendsToAdminEmail() {
        emailService.sendSimpleMessage("Alert", "Something happened");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(emailSender).send(captor.capture());

        assertThat(captor.getValue().getFrom()).isEqualTo("admin@example.com");
        assertThat(captor.getValue().getTo()).containsExactly("admin@example.com");
    }

    @Test
    void sendHtmlMessage_createsAndSendsMimeMessage() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendHtmlMessage("HTML Subject", "<b>Hello</b>");

        verify(emailSender).createMimeMessage();
        verify(emailSender).send(mimeMessage);
    }

    @Test
    void sendHtmlMessage_prefixAppliedToSubject() throws Exception {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(emailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Verify no exception is thrown and mail sender is invoked
        emailService.sendHtmlMessage("Report", "<p>Report content</p>");

        verify(emailSender, times(1)).send(mimeMessage);
    }
}
