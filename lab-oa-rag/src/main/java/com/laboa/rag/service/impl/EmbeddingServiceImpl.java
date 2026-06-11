package com.laboa.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.rag.service.EmbeddingService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 阿里云文本嵌入服务实现
 * 调用 DashScope Embedding API，输出 2048 维向量
 * 带重试和限流处理
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    // 优先读 DashScope 官方环境变量 DASHSCOPE_API_KEY，其次 properties
    @Value("${DASHSCOPE_API_KEY:${ali.embedding.api-key:sk-Zyxwvutsrqponml987654}}")
    private String apiKey;

    @Value("${ali.embedding.endpoint:https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding}")
    private String endpoint;

    @Value("${ali.embedding.model:text-embedding-v4}")
    private String model;

    /** 向量维度（text-embedding-v4 默认 1024，需显式指定 2048 才能与 ES dense_vector dims 匹配） */
    @Value("${ali.embedding.dimension:2048}")
    private int dimension;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 1;
    /** 重试间隔(ms) */
    private static final int RETRY_DELAY_MS = 200;
    /** 批量嵌入最大数量（DashScope text-embedding-v3/v4 支持 10 条/批） */
    private static final int BATCH_SIZE = 10;
    /** 单个 API 调用超时(ms) - Embedding 请求需要数秒处理 */
    private static final int API_TIMEOUT_MS = 30_000;
    /** 连接超时(ms) */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** 零向量缓存 - 当 API 不可用时直接返回 */
    private volatile float[] zeroVector;
    /** API 是否可用（启动时检测） */
    private volatile boolean apiAvailable = true;

    @PostConstruct
    public void init() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofMillis(API_TIMEOUT_MS))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(API_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS))
                           .addHandlerLast(new WriteTimeoutHandler(5)));

        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))  // 16MB，Embedding API 返回 2048 维向量响应体较大
                .build();

        // 校验 apiKey 是否来自环境变量
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 8) + "***" + apiKey.substring(apiKey.length() - 4)
                : "NULL";
        log.info("EmbeddingService 初始化完成, endpoint={}, model={}, dimension={}, apiKey={}",
                endpoint, model, dimension, maskedKey);

        // 预检测：API key 是占位符或无意义值时直接标记为不可用
        if (apiKey == null || apiKey.isBlank()
                || apiKey.contains("你的")
                || apiKey.startsWith("sk-Zyx")  // 默认占位符
                || apiKey.equals("sk-你的阿里云DashScope密钥")) {
            log.warn("Embedding API key 未配置，Embedding 将跳过（返回零向量）。请设置环境变量 DASHSCOPE_API_KEY");
            apiAvailable = false;
            return;
        }

        // 快速 ping 检测 API 是否可达（DashScope 原生 API 格式）
        try {
            Map<String, Object> pingBody = new HashMap<>();
            pingBody.put("model", model);
            Map<String, Object> pingInput = new HashMap<>();
            pingInput.put("texts", List.of("ping"));
            pingBody.put("input", pingInput);

            webClient.post()
                    .headers(h -> h.setBearerAuth(apiKey))
                    .bodyValue(pingBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            log.info("Embedding API 可达性检测通过");
        } catch (Exception e) {
            log.warn("Embedding API 不可达: {}，Embedding 将降级运行（返回零向量）", e.getMessage());
            apiAvailable = false;
        }
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();

        // 分批处理，每批最多 BATCH_SIZE 条
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            List<float[]> batchResult = callEmbeddingApiWithRetry(batch);
            allEmbeddings.addAll(batchResult);
        }

        return allEmbeddings;
    }

    /**
     * 带重试的嵌入API调用
     * 如果 API 被标记为不可用（apiAvailable=false），直接返回零向量，避免无效等待
     */
    private List<float[]> callEmbeddingApiWithRetry(List<String> texts) {
        // API 不可用时快速短路：直接返回零向量，避免超时等待
        if (!apiAvailable) {
            log.debug("Embedding API 不可用，返回零向量 ({} 条)", texts.size());
            List<float[]> fallback = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                fallback.add(new float[dimension]);
            }
            return fallback;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return callEmbeddingApi(texts);
            } catch (WebClientResponseException e) {
                lastException = e;
                // 即使是 200 也可能抛 WebClientResponseException（如 body 解析失败），打印完整响应体
                log.warn("嵌入API调用失败(第{}/{}次): HTTP {} {}, body={}",
                        attempt, MAX_RETRIES, e.getStatusCode(), e.getStatusText(),
                        e.getResponseBodyAsString().length() > 300
                                ? e.getResponseBodyAsString().substring(0, 300) + "..."
                                : e.getResponseBodyAsString());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("嵌入API调用失败(第{}/{}次): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("嵌入API调用最终失败，返回零向量降级", lastException);
        List<float[]> fallback = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            fallback.add(new float[dimension]);
        }
        return fallback;
    }

    /**
     * 调用阿里 DashScope Embedding API（原生接口）
     * 请求格式: { model, input: { texts: [...] }, parameters: { dimension: 2048 } }
     * 响应格式: { output: { embeddings: [{ embedding: [...], text_index: 0 }] } }
     */
    private List<float[]> callEmbeddingApi(List<String> texts) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        Map<String, Object> input = new HashMap<>();
        input.put("texts", texts);
        requestBody.put("input", input);
        // text-embedding-v4 默认返回 1024 维，显式指定 dimension=2048
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dimension", dimension);
        requestBody.put("parameters", parameters);

        String responseJson = webClient.post()
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(API_TIMEOUT_MS))
                .block();

        JsonNode root = objectMapper.readTree(responseJson);

        // 检查 DashScope 是否返回了业务错误
        String respCode = root.has("code") ? root.path("code").asText() : null;
        if (respCode != null && !"OK".equals(respCode)) {
            String respMsg = root.has("message") ? root.path("message").asText() : "未知错误";
            log.error("DashScope API 返回业务错误: code={}, message={}, response={}",
                    respCode, respMsg,
                    responseJson.length() > 500 ? responseJson.substring(0, 500) : responseJson);
            throw new RuntimeException("DashScope API error: " + respCode + " - " + respMsg);
        }

        List<float[]> embeddings = new ArrayList<>();

        // 解析响应: output.embeddings[].embedding
        JsonNode embeddingsNode = root.path("output").path("embeddings");
        if (embeddingsNode.isArray()) {
            for (JsonNode item : embeddingsNode) {
                JsonNode embeddingNode = item.path("embedding");
                float[] vector = new float[embeddingNode.size()];
                for (int j = 0; j < embeddingNode.size(); j++) {
                    vector[j] = (float) embeddingNode.get(j).asDouble();
                }
                embeddings.add(vector);
            }
        }

        if (embeddings.size() != texts.size()) {
            throw new RuntimeException("嵌入返回数量不匹配: 请求" + texts.size() + "条, 返回" + embeddings.size() + "条");
        }
        return embeddings;
    }
}
