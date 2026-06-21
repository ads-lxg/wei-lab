package com.laboa.literature.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.literature.service.DoiParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * DOI 解析服务实现 — 通过 Crossref API 批量查询 DOI 元数据
 *
 * 使用线程池并发查询，无需持久化存储。
 * Crossref API: GET https://api.crossref.org/works/{doi}
 */
@Slf4j
@Service
public class DoiParseServiceImpl implements DoiParseService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    /** 线程池：并发查询DOI，最多8个并发 */
    private final ExecutorService executor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final String CROSSREF_BASE = "https://api.crossref.org/works/";
    private static final int TIMEOUT_SECONDS = 15;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public List<DoiInfo> parseBatch(List<String> dois) {
        if (dois == null || dois.isEmpty()) return List.of();

        // 并发查询每个DOI
        List<CompletableFuture<DoiInfo>> futures = dois.stream()
                .map(doi -> CompletableFuture.supplyAsync(() -> fetchDoiInfo(doi), executor))
                .toList();

        // 等待所有查询完成
        return futures.stream()
                .map(f -> {
                    try {
                        return f.get(TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("DOI查询超时或异常: {}", e.getMessage());
                        return DoiInfo.empty("");
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询单个 DOI 的元数据
     */
    private DoiInfo fetchDoiInfo(String doi) {
        if (doi == null || doi.isBlank()) return DoiInfo.empty(doi);

        String cleanDoi = doi.trim();
        // 处理 http://doi.org/ 前缀
        if (cleanDoi.startsWith("http")) {
            cleanDoi = cleanDoi.replaceAll("https?://(dx\\.)?doi\\.org/", "");
        }

        try {
            String url = CROSSREF_BASE + cleanDoi;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "LabOA/1.0 (mailto:admin@laboa.local)");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            String resp = response.getBody();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode msg = root.path("message");

            if (msg.isMissingNode()) {
                log.info("DOI未找到: {}", cleanDoi);
                return DoiInfo.empty(cleanDoi);
            }

            // 解析标题
            String title = extractTitle(msg);

            // 解析作者
            String authors = extractAuthors(msg);

            // 解析摘要
            String abstractText = extractAbstract(msg);

            // 解析发表日期
            String publishDate = extractPublishDate(msg);

            // 解析来源期刊
            String sourceJournal = extractContainerTitle(msg);

            return new DoiInfo(cleanDoi, title, authors, "", abstractText, publishDate, sourceJournal, true);

        } catch (Exception e) {
            log.warn("DOI查询失败: doi={}, msg={}", cleanDoi, e.getMessage());
            return DoiInfo.empty(cleanDoi);
        }
    }

    private String extractTitle(JsonNode msg) {
        JsonNode titles = msg.path("title");
        if (titles.isArray() && titles.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode t : titles) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(t.asText());
            }
            String title = sb.toString().trim();
            // 替换 HTML/XML 标签
            return title.replaceAll("<[^>]+>", "");
        }
        return "";
    }

    private String extractAuthors(JsonNode msg) {
        JsonNode authors = msg.path("author");
        if (authors.isArray() && authors.size() > 0) {
            List<String> names = new ArrayList<>();
            for (JsonNode author : authors) {
                String given = author.path("given").asText("");
                String family = author.path("family").asText("");
                if (!family.isBlank()) {
                    names.add(family + (given.isBlank() ? "" : " " + given));
                } else if (!given.isBlank()) {
                    names.add(given);
                }
            }
            return String.join(", ", names);
        }
        return "";
    }

    private String extractAbstract(JsonNode msg) {
        String abs = msg.path("abstract").asText();
        if (abs.isBlank()) return "";
        // 去除 XML/HTML 标签
        return abs.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String extractPublishDate(JsonNode msg) {
        // 优先 published-print，其次 published-online，最后 issued
        String[] dateKeys = {"published-print", "published-online", "issued"};
        for (String key : dateKeys) {
            JsonNode node = msg.path(key);
            if (!node.isMissingNode()) {
                JsonNode dateParts = node.path("date-parts");
                if (dateParts.isArray() && dateParts.size() > 0) {
                    JsonNode parts = dateParts.get(0);
                    if (parts.isArray() && parts.size() >= 2) {
                        int year = parts.get(0).asInt();
                        int month = parts.get(1).asInt();
                        int day = parts.size() >= 3 ? parts.get(2).asInt() : 1;
                        return String.format("%04d-%02d-%02d", year, month, day);
                    } else if (parts.isArray() && parts.size() == 1) {
                        return String.valueOf(parts.get(0).asInt());
                    }
                }
            }
        }
        return "";
    }

    private String extractContainerTitle(JsonNode msg) {
        JsonNode containers = msg.path("container-title");
        if (containers.isArray() && containers.size() > 0) {
            return containers.get(0).asText().trim();
        }
        return "";
    }
}
