package com.laboa.literature.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laboa.common.exception.BusinessException;
import com.laboa.literature.entity.DownloadLog;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.mapper.DownloadLogMapper;
import com.laboa.literature.mapper.LiteratureMapper;
import com.laboa.literature.service.LiteratureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureServiceImpl implements LiteratureService {

    private final LiteratureMapper literatureMapper;
    private final DownloadLogMapper downloadLogMapper;

    @Override
    public Literature getById(Long id) {
        Literature literature = literatureMapper.selectById(id);
        if (literature == null) {
            throw new BusinessException("文献不存在");
        }
        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, id);
        wrapper.setSql("view_count = view_count + 1");
        literatureMapper.update(wrapper);
        return literature;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDownload(Long literatureId, Long userId) {
        DownloadLog log = new DownloadLog();
        log.setUserId(userId);
        log.setLiteratureId(literatureId);
        log.setDownloadTime(LocalDateTime.now());
        downloadLogMapper.insert(log);

        LambdaUpdateWrapper<Literature> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Literature::getId, literatureId);
        wrapper.setSql("download_count = download_count + 1");
        literatureMapper.update(wrapper);
    }
}
