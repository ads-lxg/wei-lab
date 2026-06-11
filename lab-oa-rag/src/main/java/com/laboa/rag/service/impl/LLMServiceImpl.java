package com.laboa.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.rag.service.LLMService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * DeepSeek LLM 服务实现
 * 兼容 OpenAI 格式，使用 WebClient 调用流式 API
 */
@Slf4j
@Service
public class LLMServiceImpl implements LLMService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-v4-flash}")
    private String model;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(120))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120))
                           .addHandlerLast(new WriteTimeoutHandler(30)));

        String finalBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(finalBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        String maskedKey = apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 8) + "***" + apiKey.substring(apiKey.length() - 4)
                : "未配置";
        log.info("LLMService 初始化完成, baseUrl={}, model={}, apiKey={}", finalBaseUrl, model, maskedKey);
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk-你的")) {
            log.error("!!! DeepSeek API Key 未正确配置，请在环境变量中设置 DEEPSEEK_API_KEY !!!");
        }
    }

    @Override
    public Flux<String> streamChat(List<Map<String, String>> messages) {
        Map<String, Object> requestBody = buildRequestBody(messages, true);
        log.info("LLM请求: model={}, messagesCount={}, stream=true", model, messages.size());

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("LLM API 错误响应: status={}, body={}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("LLM API错误: " + response.statusCode() + " - " + errorBody));
                                })
                )
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(120))
                .doOnSubscribe(s -> log.info("LLM流式请求已发送, 等待响应..."))
                .doOnComplete(() -> log.info("LLM流式响应完成"))
                .doOnError(e -> log.error("LLM流式响应异常: {}", e.getMessage()))
                .doOnNext(raw -> {
                    // 打印前 20 条原始响应行，方便排查
                    log.info("LLM原始响应行: {}", raw.length() > 300 ? raw.substring(0, 300) + "..." : raw);
                })
                // 按行拆分（响应可能是 JSON Lines 格式，多行合并在一个 chunk 中）
                .flatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String trimmed = line.trim();
                    // 兼容 SSE 格式 (data: {...}) 和 JSON Lines 格式 ({...})
                    if (trimmed.startsWith("data:")) {
                        trimmed = trimmed.substring(5).trim();
                    }
                    return trimmed;
                })
                .filter(data -> !"[DONE]".equals(data))
                .mapNotNull(data -> {
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                        if (!delta.isMissingNode() && !delta.isNull()) {
                            String text = delta.asText();
                            // 跳过空 content（role 赋值 chunk，如 {"delta":{"role":"assistant","content":""}}）
                            return text.isEmpty() ? null : text;
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("解析响应失败: data={}, error={}", data.substring(0, Math.min(data.length(), 100)), e.getMessage());
                        return null;
                    }
                })
                .switchIfEmpty(Flux.just("LLM 返回了空响应，请检查 API Key 和模型配置是否正确。"))
                .onErrorResume(e -> {
                    String msg = e.getMessage();
                    Throwable cause = e.getCause();
                    // 多级穿透找根因（DNS/网络异常经常包装多层）
                    while (cause != null && cause.getMessage() != null && !cause.getMessage().contains("resolve")) {
                        cause = cause.getCause();
                    }
                    String rootMsg = cause != null ? cause.getMessage() : "";

                    if (msg != null) {
                        if (msg.contains("timeout") || msg.contains("Timeout")) {
                            return Flux.just("请求超时，请稍后重试。");
                        }
                        if (msg.contains("Failed to resolve") || (rootMsg != null && rootMsg.contains("Failed to resolve"))) {
                            return Flux.just("DNS解析失败，无法连接 api.deepseek.com。请检查网络是否连通，能否 ping 通 api.deepseek.com。如果是内网环境，可能需要配置代理或 DNS。");
                        }
                        if (msg.contains("Connection refused") || msg.contains("connection refused")) {
                            return Flux.just("连接被拒绝，请检查 api.deepseek.com 是否可达（可能需要代理/VPN）。");
                        }
                        if (msg.contains("401") || msg.contains("UNAUTHORIZED") || msg.contains("Unauthorized")) {
                            return Flux.just("API Key 认证失败(401)，请检查 DEEPSEEK_API_KEY 环境变量是否正确配置。");
                        }
                        if (msg.contains("403") || msg.contains("FORBIDDEN")) {
                            return Flux.just("API 访问被拒绝(403)，请检查 API Key 权限。");
                        }
                    }
                    return Flux.just("对话服务异常: " + (msg != null ? msg.substring(0, Math.min(msg.length(), 100)) : "未知错误"));
                });
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> requestBody = buildRequestBody(messages, false);

        try {
            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(responseJson);
            return node.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("非流式LLM调用失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 构建 OpenAI 兼容请求体
     */
    private Map<String, Object> buildRequestBody(List<Map<String, String>> messages, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("temperature", 0.7);
        body.put("max_tokens", 4096);
        // 禁用思考模式，确保返回 content 而非 reasoning_content
        body.put("thinking", Map.of("type", "disabled"));
        return body;
    }
}
