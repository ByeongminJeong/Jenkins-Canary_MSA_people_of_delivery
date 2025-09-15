package com.example.cloudfour.authservice.domain.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    @Override
    public void sendSimpleMessage(String toEmail, String title, String content) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(toEmail);
        helper.setSubject(title);
        helper.setText(content, true);
        helper.setReplyTo("rlawldbs1007@gmail.com");
        try{
            emailSender.send(message);
        } catch (RuntimeException e){
            e.printStackTrace();
            throw new RuntimeException("Unable to send email in sendEmail", e);
        }
    }
}