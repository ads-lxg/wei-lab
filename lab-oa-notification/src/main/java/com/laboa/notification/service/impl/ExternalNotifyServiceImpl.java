package com.laboa.notification.service.impl;

import com.laboa.notification.service.ExternalNotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExternalNotifyServiceImpl implements ExternalNotifyService {

    @Override
    public boolean sendSms(String phone, String content) {
        log.info("发送短信: phone={}, content={}", phone, content);
        return true;
    }

    @Override
    public boolean sendWechat(String openid, String content) {
        log.info("发送微信: openid={}, content={}", openid, content);
        return true;
    }
}