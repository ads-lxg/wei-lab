package com.laboa.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laboa.common.constant.MqConstants;
import com.laboa.common.search.DocIdValidator;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文献ID校验器 — 过滤ES搜索结果中已删除/不在有效目录中的文献
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureIdValidatorImpl implements DocIdValidator {

    private final LiteratureMapper literatureMapper;

    @Override
    public String getDocType() {
        return MqConstants.DOC_TYPE_LITERATURE;
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

        // 查询MySQL中未删除且目录有效的文献ID
        LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Literature::getId)
               .in(Literature::getId, longIds)
               .isNotNull(Literature::getFolderId)
               .inSql(Literature::getFolderId, "SELECT id FROM rag_folder WHERE deleted = 0");
        List<Literature> lits = literatureMapper.selectList(wrapper);

        Set<String> validIds = lits.stream()
                .map(l -> String.valueOf(l.getId()))
                .collect(Collectors.toSet());

        log.debug("文献ID校验: 输入={}, 有效={}", docIds.size(), validIds.size());
        return validIds;
    }
}
