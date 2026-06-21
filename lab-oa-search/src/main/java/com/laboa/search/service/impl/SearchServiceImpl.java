package com.laboa.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laboa.search.service.SearchHitVO;
import com.laboa.search.service.SearchResult;
import com.laboa.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索服务 — 混合检索（向量 + BM25 关键词）
 *
 * 搜索链路（以文献搜索为例）：
 *   1. BM25 全文本检索 resource_text 索引（主检索，返回文献级别结果）
 *   2. k-NN 向量检索 doc_chunks 索引（辅助检索，跨语言语义匹配，结果映射回文献级别）
 *   3. 结果合并、去重、加权排序
 *
 * 重要设计：
 *   - 两种检索都按 docType 过滤，确保只返回对应类型的文档
 *   - 向量检索在 doc_chunks 中按 docId 聚合，映射回文献级别
 *   - BM25 在 resource_text 中直接返回文献级别结果
 *   - 调用方（如 DocumentManagementServiceImpl）通过 MySQL 回查确保结果有效
 *
 * 当前使用模型：阿里 DashScope text-embedding-v4（中英跨语言）
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${DASHSCOPE_API_KEY:${ali.embedding.api-key:}}")
    private String embeddingApiKey;

    @Value("${ali.embedding.endpoint:https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding}")
    private String embeddingEndpoint;

    @Value("${ali.embedding.model:text-embedding-v4}")
    private String embeddingModel;

    @Value("${ali.embedding.dimension:2048}")
    private int embeddingDimension;

    private RestTemplate embeddingRestTemplate;
    /** embedding 是否可用（启动时检测） */
    private volatile boolean embeddingAvailable = true;
    /** embedding 最后一次失败时间，用于冷却重试（避免永久禁用） */
    private volatile long lastEmbeddingFailTime = 0L;
    /** 冷却时间 5 分钟，超过后自动重试 */
    private static final long EMBEDDING_COOLDOWN_MS = 5 * 60 * 1000L;

    private static final String INDEX_CHUNKS = "doc_chunks";
    private static final String INDEX_FULLTEXT = "resource_text";

    public SearchServiceImpl(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @PostConstruct
    public void init() {
        // 给 RestTemplate 设置超时（与 EmbeddingServiceImpl 的 WebClient 一致）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);  // 连接超时 10s
        factory.setReadTimeout(30_000);     // 读取超时 30s
        this.embeddingRestTemplate = new RestTemplate(factory);

        // 与 EmbeddingServiceImpl 一致的 API key 校验
        if (embeddingApiKey == null || embeddingApiKey.isBlank()
                || embeddingApiKey.contains("你的")
                || embeddingApiKey.startsWith("sk-Zyx")
                || embeddingApiKey.equals("sk-你的阿里云DashScope密钥")) {
            log.warn("Embedding API key 未配置或为占位符，向量检索将跳过（仅使用BM25关键词检索）。请设置环境变量 DASHSCOPE_API_KEY 或 ALI_EMBEDDING_API_KEY");
            embeddingAvailable = false;
        } else {
            String maskedKey = embeddingApiKey.length() > 10
                    ? embeddingApiKey.substring(0, 8) + "***" + embeddingApiKey.substring(embeddingApiKey.length() - 4)
                    : "NULL";
            log.info("Embedding 已配置, apiKey={}, model={}, dimension={}。KNN向量检索可用（支持中英跨语言搜索）",
                    maskedKey, embeddingModel, embeddingDimension);
            embeddingAvailable = true;
        }
    }

    @Override
    public SearchResult search(String keyword, String type, int page, int size) {
        return search(keyword, type, page, size, null);
    }

    /**
     * 搜索（支持排除已删除的文档ID）
     * @param excludeDocIds 需要排除的文档ID集合（如已逻辑删除的文献）
     */
    @Override
    public SearchResult search(String keyword, String type, int page, int size, java.util.Set<String> excludeDocIds) {
        return search(keyword, type, page, size, excludeDocIds, "hybrid");
    }

    /**
     * 搜索（支持搜索模式）
     * @param mode 搜索模式: "bm25"=BM25关键词优先(精确查询), "knn"=KNN语义检索优先(模糊查询), 默认"hybrid"均衡混合
     *
     * 搜索流程：
     * 1. BM25检索 resource_text（文献级别，docType过滤）
     * 2. KNN检索 doc_chunks（片段级别，按docId聚合回文献级别，docType过滤）
     * 3. 归一化分数 + 加权合并 + 去重
     * 4. 排除已删除文档
     * 5. 按加权分数排序 + 分页
     */
    @Override
    public SearchResult search(String keyword, String type, int page, int size, java.util.Set<String> excludeDocIds, String mode) {
        int from = (page - 1) * size;

        // 根据搜索模式决定各路检索的候选数量和权重
        // bm25模式：BM25候选多、权重高；knn模式：向量候选多、权重高；hybrid：均衡
        int vectorCandidateSize, bm25CandidateSize;
        double vectorWeight, bm25Weight;

        if ("bm25".equals(mode)) {
            // BM25优先模式：关键词精确匹配为主
            vectorCandidateSize = size * 3;
            bm25CandidateSize = size * 10;
            vectorWeight = 0.3;
            bm25Weight = 0.7;
        } else if ("knn".equals(mode)) {
            // KNN优先模式：语义检索为主，跨语言支持更好
            vectorCandidateSize = size * 10;
            bm25CandidateSize = size * 3;
            vectorWeight = 0.7;
            bm25Weight = 0.3;
        } else {
            // 均衡混合模式：KNN和BM25各取充足候选，保证召回率
            vectorCandidateSize = Math.max(size * 5, 100);
            bm25CandidateSize = Math.max(size * 5, 100);
            vectorWeight = 0.5;
            bm25Weight = 0.5;
        }

        // 1. BM25 全文本检索（resource_text 索引，文献级别，主检索）
        List<SearchHitVO> bm25Hits = bm25Search(keyword, type, bm25CandidateSize);

        // 2. 向量检索（doc_chunks 索引，片段级别→按docId聚合回文献级别，辅助检索）
        List<SearchHitVO> vectorHits = vectorSearch(keyword, type, vectorCandidateSize);

        log.info("搜索诊断: keyword='{}', type={}, mode={}, BM25命中={}, KNN命中={}, embeddingAvailable={}",
                keyword, type, mode, bm25Hits.size(), vectorHits.size(), embeddingAvailable);

        // 3. 合并去重 + 加权评分
        // 归一化各路检索的分数，然后按权重合并
        Map<String, SearchHitVO> merged = new LinkedHashMap<>();

        // 归一化向量检索分数
        double maxVectorScore = vectorHits.stream().mapToDouble(SearchHitVO::getScore).max().orElse(1.0);
        if (maxVectorScore <= 0) maxVectorScore = 1.0;
        for (SearchHitVO h : vectorHits) {
            h.setScore((h.getScore() / maxVectorScore) * vectorWeight);
            merged.put(h.getDocId(), h);
        }

        // 归一化BM25分数并合并
        double maxBm25Score = bm25Hits.stream().mapToDouble(SearchHitVO::getScore).max().orElse(1.0);
        if (maxBm25Score <= 0) maxBm25Score = 1.0;
        for (SearchHitVO h : bm25Hits) {
            double normalizedScore = (h.getScore() / maxBm25Score) * bm25Weight;
            SearchHitVO existing = merged.get(h.getDocId());
            if (existing != null) {
                // 同一文档在两路都出现，分数相加
                existing.setScore(existing.getScore() + normalizedScore);
            } else {
                h.setScore(normalizedScore);
                merged.put(h.getDocId(), h);
            }
        }

        // 4. 排除已删除的文档
        if (excludeDocIds != null && !excludeDocIds.isEmpty()) {
            merged.keySet().removeAll(excludeDocIds);
        }

        // 5. 按加权分数排序
        List<SearchHitVO> allHits = new ArrayList<>(merged.values());
        allHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // 6. 分页截取
        int endIdx = Math.min(from + size, allHits.size());
        List<SearchHitVO> pageHits = from < allHits.size()
                ? allHits.subList(from, endIdx)
                : Collections.emptyList();

        return new SearchResult(allHits.size(), pageHits);
    }

    /**
     * 将 ES 返回的 docId 统一转为字符串数字，避免 Integer/Long/Double 不一致导致去重失败
     */
    private String normalizeDocId(Object val) {
        if (val == null) return "";
        if (val instanceof Number) {
            return String.valueOf(((Number) val).longValue());
        }
        String s = String.valueOf(val);
        // 兜底：去掉可能的小数点 "5.0" -> "5"
        if (s.contains(".")) {
            try {
                return String.valueOf(Long.parseLong(s.substring(0, s.indexOf('.'))));
            } catch (NumberFormatException ignored) {}
        }
        return s;
    }

    /**
     * 调用 DashScope Embedding API 获取查询向量
     * 改进：重试机制 + 智能冷却（区分临时错误和致命错误）+ 详细日志
     */
    private float[] embed(String text) {
        // 冷却重试：如果之前失败，检查是否已过冷却期
        if (!embeddingAvailable) {
            long elapsed = System.currentTimeMillis() - lastEmbeddingFailTime;
            if (elapsed < EMBEDDING_COOLDOWN_MS) {
                log.debug("Embedding 冷却中（剩余{}秒），跳过向量检索", (EMBEDDING_COOLDOWN_MS - elapsed) / 1000);
                return new float[0];
            }
            log.info("Embedding 冷却期已过，尝试重新调用...");
            embeddingAvailable = true;
        }

        // 重试机制（与 EmbeddingServiceImpl 一致：1 次重试）
        int maxRetries = 2;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                float[] result = callEmbeddingApi(text);
                if (result.length > 0) {
                    return result;
                }
                // 返回空但没抛异常（业务错误），已在 callEmbeddingApi 内处理冷却
                return new float[0];
            } catch (HttpClientErrorException.Unauthorized
                     | HttpClientErrorException.Forbidden e) {
                // 401/403：密钥无效，致命错误，立即冷却
                log.error("Embedding API 认证失败(HTTP {}): {}。请检查 API Key 是否正确。",
                        e.getStatusCode().value(), e.getResponseBodyAsString());
                markEmbeddingFailed();
                return new float[0];
            } catch (HttpClientErrorException e) {
                // 4xx（非401/403）：可能是请求格式错误、限流等
                String body = e.getResponseBodyAsString();
                log.warn("Embedding API 客户端错误(第{}/{}次, HTTP {}): body={}",
                        attempt, maxRetries, e.getStatusCode().value(),
                        body.length() > 300 ? body.substring(0, 300) + "..." : body);
                lastException = e;
                // 429 限流：冷却
                if (e.getStatusCode().value() == 429) {
                    log.warn("Embedding API 限流(429)，进入冷却期");
                    markEmbeddingFailed();
                    return new float[0];
                }
            } catch (HttpServerErrorException e) {
                // 5xx：服务器错误，临时错误，重试
                log.warn("Embedding API 服务器错误(第{}/{}次, HTTP {}): {}",
                        attempt, maxRetries, e.getStatusCode().value(), e.getMessage());
                lastException = e;
            } catch (Exception e) {
                // 网络超时、JSON 解析等：临时错误，重试
                log.warn("Embedding 调用异常(第{}/{}次): {}",
                        attempt, maxRetries, e.getMessage());
                lastException = e;
            }
            // 重试前等待
            if (attempt < maxRetries) {
                try { Thread.sleep(200L * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有重试都失败
        // 临时错误（超时、5xx、网络）不触发长时间冷却，只本次跳过
        // 这样下次搜索仍会尝试 KNN，避免一次网络抖动导致 5 分钟全量禁用
        log.warn("Embedding 全部重试失败（临时错误，不触发冷却，下次搜索将重试）: {}",
                lastException != null ? lastException.getMessage() : "unknown");
        return new float[0];
    }

    /**
     * 实际调用 DashScope Embedding API
     * @return 向量；业务错误时返回空数组并可能触发冷却
     */
    private float[] callEmbeddingApi(String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        Map<String, Object> input = new HashMap<>();
        input.put("texts", List.of(text));
        body.put("input", input);
        Map<String, Object> params = new HashMap<>();
        params.put("dimension", embeddingDimension);
        body.put("parameters", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(embeddingApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = embeddingRestTemplate.postForEntity(
                embeddingEndpoint, entity, String.class);
        String resp = response.getBody();

        log.debug("Embedding API 响应: HTTP {}, body长度={}", response.getStatusCode().value(),
                resp != null ? resp.length() : 0);

        JsonNode root = objectMapper.readTree(resp);

        // 检查 DashScope 业务错误
        String respCode = root.has("code") ? root.path("code").asText() : null;
        if (respCode != null && !"OK".equals(respCode)) {
            String respMsg = root.has("message") ? root.path("message").asText() : "未知错误";
            log.warn("DashScope Embedding 返回业务错误: code={}, message={}", respCode, respMsg);
            // 认证/密钥类错误才冷却，其他错误（如参数错误）只跳过本次
            if (respCode.contains("ApiKey") || respCode.contains("Auth") || respCode.contains("Permission")) {
                markEmbeddingFailed();
            }
            return new float[0];
        }

        JsonNode embeddings = root.path("output").path("embeddings");
        if (embeddings.isArray() && embeddings.size() > 0) {
            JsonNode vecNode = embeddings.get(0).path("embedding");
            float[] vec = new float[vecNode.size()];
            for (int i = 0; i < vecNode.size(); i++) vec[i] = (float) vecNode.get(i).asDouble();

            // 检查是否为零向量（可能 API 返回了空向量）
            boolean allZero = true;
            for (float v : vec) {
                if (v != 0f) { allZero = false; break; }
            }
            if (allZero) {
                log.warn("Embedding 返回了零向量（dim={}），可能 API 异常", vec.length);
                return new float[0];
            }

            log.debug("Embedding 调用成功: dim={}", vec.length);
            return vec;
        }
        log.warn("Embedding 返回空向量（output.embeddings 为空）");
        return new float[0];
    }

    private void markEmbeddingFailed() {
        embeddingAvailable = false;
        lastEmbeddingFailTime = System.currentTimeMillis();
    }

    /**
     * k-NN 向量检索 — 搜索 doc_chunks 索引
     * 利用 text-embedding-v4 的多语言能力，中文查询也能找到英文内容
     * 在 ES 层面按 docType 过滤，确保只返回对应类型的文档
     */
    private List<SearchHitVO> vectorSearch(String keyword, String docType, int size) {
        try {
            float[] queryVector = embed(keyword);
            if (queryVector.length == 0) {
                log.info("KNN跳过: embed()返回空向量, keyword='{}', embeddingAvailable={}", keyword, embeddingAvailable);
                return Collections.emptyList();
            }

            List<Float> vec = new ArrayList<>(queryVector.length);
            for (float v : queryVector) vec.add(v);

            final String filterDocType = (docType != null && !docType.isEmpty() && !"all".equals(docType)) ? docType : null;
            final long numCandidates = Math.max(size * 10L, 200L);

            log.info("KNN检索: keyword='{}', docType={}, vecDim={}, numCandidates={}, size={}",
                    keyword, filterDocType, vec.size(), numCandidates, size);

            SearchResponse<Map> response = elasticsearchClient.search(s -> {
                s.index(INDEX_CHUNKS)
                 .size(size)
                 .query(q -> q.knn(k -> {
                     k.field("embedding")
                      .queryVector(vec)
                      .numCandidates(numCandidates);
                     // 在 ES 层面按 docType 过滤，避免内存过滤导致候选不足
                     if (filterDocType != null) {
                         k.filter(f -> f.term(t -> t.field("docType").value(filterDocType)));
                     }
                     return k;
                 }));
                return s;
            }, Map.class);

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            log.info("KNN结果: keyword='{}', docType={}, ES返回hits={}, total={}",
                    keyword, filterDocType, response.hits().hits().size(), totalHits);

            // 按 docId 去重聚合：同一个文档多个 chunk，取最高分
            Map<String, SearchHitVO> docMap = new LinkedHashMap<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                String chunkDocType = String.valueOf(source.getOrDefault("docType", ""));
                String docId = normalizeDocId(source.getOrDefault("docId", ""));

                // 双重保险：ES filter 已过滤，这里再校验一次
                if (filterDocType != null && !filterDocType.equals(chunkDocType)) continue;

                double score = hit.score() != null ? hit.score() : 0.0;
                SearchHitVO existing = docMap.get(docId);
                if (existing == null || score > existing.getScore()) {
                    SearchHitVO vo = new SearchHitVO();
                    vo.setDocId(docId);
                    vo.setTitle(String.valueOf(source.getOrDefault("fileName", "")));
                    vo.setFileName(String.valueOf(source.getOrDefault("fileName", "")));
                    vo.setDocType(chunkDocType);
                    vo.setScore(score);
                    String content = String.valueOf(source.getOrDefault("content", ""));
                    vo.setContent(content.length() > 200 ? content.substring(0, 200) + "..." : content);
                    docMap.put(docId, vo);
                }
            }
            log.debug("KNN检索成功: keyword={}, docType={}, hits={}", keyword, filterDocType, docMap.size());
            return new ArrayList<>(docMap.values());
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.info("ES索引 '{}' 尚未创建，跳过向量检索", INDEX_CHUNKS);
                return Collections.emptyList();
            }
            log.warn("向量检索失败: keyword={}, docType={}, error={}", keyword, docType, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("向量检索异常: keyword={}, docType={}, error={}", keyword, docType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * BM25 全文本检索 — 搜索 resource_text 索引
     * 支持中英文跨语言关键词匹配
     * 搜索字段：title, fileName, text（使用IK中文分析器）
     * 策略：multi_match(BestFields) 分词匹配 + match_phrase 短语匹配 + wildcard 兜底
     */
    private List<SearchHitVO> bm25Search(String keyword, String docType, int size) {
        try {
            final String filterDocType = (docType != null && !docType.isEmpty() && !"all".equals(docType)) ? docType : null;

            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index(INDEX_FULLTEXT)
                    .size(size)
                    .query(q -> q
                            .bool(b -> {
                                // 1. multi_match 分词匹配（主查询，召回率高）
                                b.should(sq -> sq.multiMatch(mm -> mm
                                        .query(keyword)
                                        .fields("title^3", "fileName^3", "text^1")
                                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                ));
                                // 2. match_phrase 短语匹配（精确度高，权重更高）
                                b.should(sq -> sq.multiMatch(mm -> mm
                                        .query(keyword)
                                        .fields("title^3", "fileName^3", "text^1")
                                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.Phrase)
                                        .boost(5.0f)
                                ));
                                // 3. wildcard 兜底：支持文件名/标题的部分匹配（不依赖分词器）
                                b.should(sq -> sq.bool(bb -> bb
                                        .should(s2 -> s2.wildcard(w -> w.field("fileName").value("*" + keyword + "*")))
                                        .should(s2 -> s2.wildcard(w -> w.field("title").value("*" + keyword + "*")))
                                ));
                                b.minimumShouldMatch("1");
                                if (filterDocType != null) {
                                    b.filter(f -> f.term(t -> t.field("docType").value(filterDocType)));
                                }
                                return b;
                            })
                    )
                    .highlight(h -> h
                            .fields("title", HighlightField.of(f -> f))
                            .fields("fileName", HighlightField.of(f -> f))
                            .fields("text", HighlightField.of(f -> f))
                    )
            , Map.class);
            List<SearchHitVO> results = new ArrayList<>();

            for (Hit<Map> hit : response.hits().hits()) {
                SearchHitVO vo = new SearchHitVO();
                Map<String, Object> source = hit.source();
                if (source != null) {
                    vo.setDocId(normalizeDocId(source.getOrDefault("resourceId",
                            source.getOrDefault("id", ""))));
                    vo.setTitle(String.valueOf(source.getOrDefault("title", "")));
                    vo.setFileName(String.valueOf(source.getOrDefault("fileName",
                            source.getOrDefault("title", ""))));
                    Object dt = source.get("docType");
                    vo.setDocType(dt != null ? String.valueOf(dt) : "fulltext");
                }
                vo.setScore(hit.score() != null ? (double) hit.score() : 0.0);

                Map<String, List<String>> highlightMap = hit.highlight();
                if (highlightMap != null && !highlightMap.isEmpty()) {
                    vo.setContent(highlightMap.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.joining(" ... ")));
                }
                results.add(vo);
            }
            log.debug("BM25检索成功: keyword={}, docType={}, hits={}", keyword, filterDocType, results.size());
            return results;
        } catch (ElasticsearchException e) {
            if (e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                log.info("ES索引 '{}' 尚未创建，BM25检索返回空", INDEX_FULLTEXT);
                return Collections.emptyList();
            }
            log.warn("BM25检索失败: keyword={}, docType={}, error={}", keyword, docType, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("BM25检索异常: keyword={}, docType={}, error={}", keyword, docType, e.getMessage());
            return Collections.emptyList();
        }
    }
}
