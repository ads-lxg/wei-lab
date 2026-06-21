package com.laboa.doc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.search.DocIdValidator;
import com.laboa.doc.entity.MdDocument;
import com.laboa.doc.mapper.MdDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内部文档ID校验器 — 过滤ES搜索结果中已删除的文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocIdValidatorImpl implements DocIdValidator {

    private final MdDocumentMapper mdDocumentMapper;

    @Override
    public String getDocType() {
        return MqConstants.DOC_TYPE_DOC;
    }

    @Override
    public Set<String> filterValidDocIds(Set<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return new HashSet<>();
        }

        // 转换为Long集合查询
        Set<Long> longIds = new HashSet<>();
        for (String id : docIds) {
            try {
                longIds.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
            }
        }

        if (longIds.isEmpty()) {
            return new HashSet<>();
        }

        // 查询MySQL中未删除的文档ID
        LambdaQueryWrapper<MdDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(MdDocument::getId)
               .in(MdDocument::getId, longIds);
        List<MdDocument> docs = mdDocumentMapper.selectList(wrapper);

        Set<String> validIds = docs.stream()
                .map(d -> String.valueOf(d.getId()))
                .collect(Collectors.toSet());

        log.debug("文档ID校验: 输入={}, 有效={}", docIds.size(), validIds.size());
        return validIds;
    }
}
