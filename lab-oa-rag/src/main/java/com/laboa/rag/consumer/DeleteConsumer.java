package com.laboa.rag.consumer;

import com.laboa.common.constant.MqConstants;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.mapper.MinioFileMapper;
import com.laboa.rag.service.ResourceTextService;
import com.laboa.rag.service.VectorStoreService;
import com.rabbitmq.client.Channel;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 删除消费者 — 监听 delete.queue，异步清理 MinIO 文件和 ES 索引
 * 手动确认 + 幂等 + 死信
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteConsumer {

    private final MinioFileMapper minioFileMapper;
    private final MinioClient minioClient;
    private final VectorStoreService vectorStoreService;
    private final ResourceTextService resourceTextService;

    @RabbitListener(queues = MqConstants.DELETE_QUEUE,
            containerFactory = "manualAckContainerFactory")
    public void onDelete(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());

        try {
            // 解析消息体
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);

            Long resourceId = node.has("resourceId") ? node.get("resourceId").asLong() : null;
            String docType = node.has("docType") ? node.get("docType").asText() : null;
            Long fileId = node.has("fileId") ? node.get("fileId").asLong() : null;
            boolean permanent = node.has("permanent") && node.get("permanent").asBoolean(false);

            if (resourceId == null || docType == null) {
                log.warn("消息格式错误，丢弃: body={}", body);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("收到删除消息: resourceId={}, docType={}, fileId={}, permanent={}", resourceId, docType, fileId, permanent);

            // 1. 删除 ES 向量索引（doc_chunks）— 逻辑删除和物理删除都需要
            try {
                vectorStoreService.deleteByDocId(docType, resourceId);
                log.info("ES doc_chunks 删除完成: resourceId={}, docType={}", resourceId, docType);
            } catch (Exception e) {
                log.error("ES doc_chunks 删除失败: resourceId={}, docType={}, error={}",
                        resourceId, docType, e.getMessage(), e);
            }

            // 2. 删除 ES 文本索引（resource_text）— 逻辑删除和物理删除都需要
            try {
                resourceTextService.deleteByResourceId(resourceId, docType);
                log.info("ES resource_text 删除完成: resourceId={}, docType={}", resourceId, docType);
            } catch (Exception e) {
                log.error("ES resource_text 删除失败: resourceId={}, docType={}, error={}",
                        resourceId, docType, e.getMessage(), e);
            }

            // 3. 删除 MinIO 文件 — 仅物理删除时执行（逻辑删除保留MinIO文件，恢复时需要重新索引）
            if (permanent && fileId != null) {
                try {
                    MinioFile minioFile = minioFileMapper.selectById(fileId);
                    if (minioFile != null) {
                        // 逻辑删除 minio_file 记录
                        minioFileMapper.deleteById(fileId);

                        // 物理删除 MinIO 对象
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                .bucket(minioFile.getBucket())
                                .object(minioFile.getStoredName())
                                .build());
                        log.info("MinIO文件删除完成(物理删除): fileId={}, storedName={}", fileId, minioFile.getStoredName());
                    }
                } catch (Exception e) {
                    // MinIO 对象不存在（RemoveObject 可能抛异常），不阻塞 ack
                    log.warn("MinIO文件删除异常（可能已不存在）: fileId={}, error={}", fileId, e.getMessage());
                }
            } else if (!permanent) {
                log.info("逻辑删除，保留MinIO文件（恢复时需重新索引ES）: resourceId={}", resourceId);
            }

            // 全部完成，确认
            channel.basicAck(deliveryTag, false);
            log.info("删除消息处理完成: resourceId={}, docType={}, permanent={}", resourceId, docType, permanent);

        } catch (Exception e) {
            log.error("删除消息处理失败，重新入队: error={}", e.getMessage(), e);
            try {
                // nack 并重新入队（会通过死信配置最终进入 DLQ）
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("nack 失败", ex);
            }
        }
    }
}