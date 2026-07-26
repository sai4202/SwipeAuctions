package com.swipeauctions.sms.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.swipeauctions.sms.service.SmsService;

/**
 * No real SMS gateway is wired up yet — logs the message that would have gone out so mobile OTP
 * delivery is visible in dev/test without a vendor. Swap in a real provider (Twilio/MSG91/etc.) by
 * replacing this bean with one hitting that vendor's API; every caller already goes through
 * {@link SmsService} so no other code changes.
 */
@Slf4j
@Service
public class LoggingSmsServiceImpl implements SmsService {

    @Override
    public void sendSms(String mobileNumber, String message) {
        log.info("[SMS STUB] To: {} | Message: {}", mobileNumber, message);
    }
}
