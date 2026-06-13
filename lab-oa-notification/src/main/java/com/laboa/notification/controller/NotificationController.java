package com.laboa.notification.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.notification.entity.Notification;
import com.laboa.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<PageResult<Notification>> page(@RequestParam(value = "isRead", required = false) Integer isRead,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "size", defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageResult<Notification> pageResult = notificationService.page(userId, isRead, page, size);
        return Result.success(pageResult);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable("id") Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = StpUtil.getLoginIdAsLong();
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        long count = notificationService.countUnread(userId);
        return Result.success(count);
    }
}