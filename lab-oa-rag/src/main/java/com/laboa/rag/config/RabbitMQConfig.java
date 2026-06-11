package com.laboa.rag.config;

import com.laboa.common.constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — 删除队列 + 死信 + 手动确认
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机 ====================

    @Bean
    public DirectExchange deleteExchange() {
        return new DirectExchange(MqConstants.DELETE_EXCHANGE);
    }

    // ==================== 死信队列 ====================

    @Bean
    public Queue deleteDlq() {
        return QueueBuilder.durable(MqConstants.DELETE_DLQ).build();
    }

    // ==================== 业务队列（绑定死信） ====================

    @Bean
    public Queue deleteQueue() {
        return QueueBuilder.durable(MqConstants.DELETE_QUEUE)
                .deadLetterExchange(MqConstants.DELETE_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DELETE_DLQ)
                .build();
    }

    @Bean
    public Binding deleteBinding() {
        return BindingBuilder.bind(deleteQueue())
                .to(deleteExchange())
                .with(MqConstants.DELETE_ROUTING_KEY);
    }

    @Bean
    public Binding deleteDlqBinding() {
        return BindingBuilder.bind(deleteDlq())
                .to(deleteExchange())
                .with(MqConstants.DELETE_DLQ);
    }

    // ==================== 全局配置 ====================

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // 发送方确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("RabbitMQ 消息发送未确认: correlationData={}, cause={}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> {
            log.warn("RabbitMQ 消息退回: exchange={}, routingKey={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText());
        });
        return template;
    }

    @Bean(name = "manualAckContainerFactory")
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        // 手动确认模式
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        // 每次取 1 条，保证公平分发
        factory.setPrefetchCount(1);
        // 并发消费者数
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}