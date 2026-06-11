package com.laboa.notification.service;

public interface ExternalNotifyService {

    boolean sendSms(String phone, String content);

    boolean sendWechat(String openid, String content);
}