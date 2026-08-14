package kr.itsdev.devjobcollector.security.signup;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationMailService {
    private final JavaMailSender mailSender;
    private final AuthSignupProperties properties;

    public VerificationMailService(JavaMailSender mailSender, AuthSignupProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendCode(String email, String code) {
        if (!properties.isMailDeliveryEnabled()) {
            if (properties.isExposeCodeInResponse()) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "이메일 인증 발송 설정이 준비되지 않았습니다.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject("[데브잡스] 이메일 인증 코드");
        message.setText("데브잡스 이메일 인증 코드는 " + code + " 입니다. "
                + properties.getVerificationMinutes() + "분 안에 입력해주세요.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인증 이메일을 발송하지 못했습니다.", ex);
        }
    }
}
