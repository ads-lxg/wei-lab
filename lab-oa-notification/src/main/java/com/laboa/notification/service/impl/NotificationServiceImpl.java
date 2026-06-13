package com.laboa.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.notification.entity.Notification;
import com.laboa.notification.mapper.NotificationMapper;
import com.laboa.notification.service.ExternalNotifyService;
import com.laboa.notification.service.NotificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExternalNotifyService externalNotifyService;

    @PostConstruct
    public void init() {
        log.info("NotificationServiceImpl 已加载");
    }

    @Override
    public void sendNotification(Long userId, String title, String content, String type, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
        log.info("通知已入库: id={}, userId={}, type={}", notification.getId(), userId, type);

        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), "/notification", notification
            );
        } catch (Exception e) {
            log.warn("WebSocket推送失败: userId={}, {}", userId, e.getMessage());
        }
    }

    @Override
    public void sendNotificationBatch(List<Long> userIds, String title, String content, String type, Long relatedId) {
        log.info("批量通知开始: userCount={}, type={}", userIds.size(), type);
        int success = 0;
        for (Long userId : userIds) {
            try {
                sendNotification(userId, title, content, type, relatedId);
                success++;
            } catch (Exception e) {
                log.error("通知发送失败: userId={}, {}", userId, e.getMessage(), e);
            }
        }
        log.info("批量通知完成: 成功={}, 失败={}", success, userIds.size() - success);
    }

    @Override
    public void sendNotificationToAll(String title, String content, String type, Long relatedId) {
        log.warn("sendNotificationToAll 未实现 - 请使用 sendNotificationBatch（需从调用方传入用户ID列表）");
    }

    @Override
    public PageResult<Notification> page(Long userId, Integer isRead, int pageNum, int size) {
        Page<Notification> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        IPage<Notification> result = notificationMapper.selectPage(page, wrapper);

        List<Notification> records = result.getRecords();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public void markAsRead(Long id) {
        Notification exist = notificationMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("通知不存在");
        }
        Notification notification = new Notification();
        notification.setId(id);
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Override
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
        );
        for (Notification n : unread) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    @Override
    public long countUnread(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);
        return notificationMapper.selectCount(wrapper);
    }
}