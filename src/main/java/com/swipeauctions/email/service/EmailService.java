package com.swipeauctions.email.service;

import com.swipeauctions.email.dto.EmailRequestDTO;

public interface EmailService {

    void sendEmail(EmailRequestDTO emailRequest);

}