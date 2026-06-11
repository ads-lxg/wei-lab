package com.laboa.notification.service;

import com.laboa.common.result.PageResult;
import com.laboa.notification.entity.Notification;

public interface NotificationService {

    void sendNotification(Long userId, String title, String content, String type, Long relatedId);

    PageResult<Notification> page(Long userId, Integer isRead, int page, int size);

    void markAsRead(Long id);

    long countUnread(Long userId);
}