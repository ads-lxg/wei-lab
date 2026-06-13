package com.laboa.notification.service;

import com.laboa.common.result.PageResult;
import com.laboa.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    void sendNotification(Long userId, String title, String content, String type, Long relatedId);

    /**
     * 批量推送通知给多个用户
     */
    void sendNotificationBatch(List<Long> userIds, String title, String content, String type, Long relatedId);

    /**
     * 推送通知给所有非游客用户
     */
    void sendNotificationToAll(String title, String content, String type, Long relatedId);

    PageResult<Notification> page(Long userId, Integer isRead, int page, int size);

    void markAsRead(Long id);

    void markAllAsRead(Long userId);

    long countUnread(Long userId);
}