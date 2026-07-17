package com.swipeauctions.email.serviceImpl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.swipeauctions.common.exception.EmailConfigurationException;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl
        implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromMail;

    @Override
    public void sendEmail(EmailRequestDTO request) {

        try {

            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true,"UTF-8");

            helper.setFrom(fromMail);

            helper.setTo(request.getTo());

            helper.setSubject(request.getSubject());

            helper.setText(request.getBody(), true);

            mailSender.send(message);

        } catch (Exception e) {

            throw new EmailConfigurationException("Failed to send email : " + e.getMessage());
        }
    }
}