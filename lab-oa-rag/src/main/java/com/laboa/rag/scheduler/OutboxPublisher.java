package com.laboa.rag.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.entity.OutboxEvent;
import com.laboa.common.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 发布器 — 定时扫描 PENDING 事件，投递到 RabbitMQ
 * 投递成功后更新状态为 SENT，失败后增加重试计数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final int BATCH_SIZE = 50;

    /**
     * 每 5 秒扫描一次 PENDING 事件
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPending() {
        List<OutboxEvent> events = outboxEventMapper.selectList(
                new LambdaQueryWrapper<OutboxEvent>()
                        .eq(OutboxEvent::getStatus, MqConstants.OUTBOX_STATUS_PENDING)
                        .last("LIMIT " + BATCH_SIZE)
        );

        if (events.isEmpty()) {
            return;
        }

        log.debug("OutboxPublisher扫描到 {} 条PENDING事件", events.size());

        for (OutboxEvent event : events) {
            try {
                // 根据事件类型发送到对应队列
                if (MqConstants.EVENT_DELETE_RESOURCE.equals(event.getEventType())) {
                    rabbitTemplate.convertAndSend(
                            MqConstants.DELETE_EXCHANGE,
                            MqConstants.DELETE_ROUTING_KEY,
                            event.getPayload()
                    );
                }
                // 更新状态为 SENT
                markSent(event);
                log.debug("Outbox事件已发送: eventId={}, eventType={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Outbox事件发送失败: eventId={}, eventType={}, error={}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
                markRetry(event);
            }
        }
    }

    @Transactional
    private void markSent(OutboxEvent event) {
        event.setStatus(MqConstants.OUTBOX_STATUS_SENT);
        outboxEventMapper.updateById(event);
    }

    @Transactional
    private void markRetry(OutboxEvent event) {
        int retryCount = event.getRetryCount() != null ? event.getRetryCount() + 1 : 1;
        event.setRetryCount(retryCount);
        if (retryCount >= MqConstants.MAX_RETRY_COUNT) {
            event.setStatus(MqConstants.OUTBOX_STATUS_FAILED);
            log.warn("Outbox事件达到最大重试次数，标记为FAILED: eventId={}, aggregateId={}",
                    event.getId(), event.getAggregateId());
        }
        outboxEventMapper.updateById(event);
    }
}