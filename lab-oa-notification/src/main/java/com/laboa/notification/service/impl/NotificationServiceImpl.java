package com.laboa.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.notification.entity.Notification;
import com.laboa.notification.mapper.NotificationMapper;
import com.laboa.notification.service.NotificationService;
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

        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/notification", notification
        );
        log.info("通知已发送: userId={}, type={}", userId, type);
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
    public long countUnread(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);
        return notificationMapper.selectCount(wrapper);
    }
}