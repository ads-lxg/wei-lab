package com.laboa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.system.dto.SysLogSearchDTO;
import com.laboa.system.entity.SysLog;
import com.laboa.system.mapper.SysLogMapper;
import com.laboa.system.service.SysLogService;
import com.laboa.system.vo.SysLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {

    private final SysLogMapper sysLogMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<SysLogVO> search(SysLogSearchDTO dto) {
        Page<SysLog> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();

        // 操作人：模糊匹配用户名或真实姓名
        if (dto.getOperator() != null && !dto.getOperator().isBlank()) {
            String keyword = dto.getOperator().trim();
            wrapper.and(w -> w
                    .like(SysLog::getUsername, keyword)
                    .or()
                    .like(SysLog::getRealName, keyword)
            );
        }

        // 操作类型：精确匹配
        if (dto.getAction() != null && !dto.getAction().isBlank()) {
            wrapper.like(SysLog::getAction, dto.getAction().trim());
        }

        // 操作目标：模糊匹配
        if (dto.getTarget() != null && !dto.getTarget().isBlank()) {
            wrapper.like(SysLog::getTarget, dto.getTarget().trim());
        }

        // 操作模块：精确匹配
        if (dto.getModule() != null && !dto.getModule().isBlank()) {
            wrapper.eq(SysLog::getModule, dto.getModule().trim());
        }

        // 操作结果
        if (dto.getResult() != null && !dto.getResult().isBlank()) {
            wrapper.eq(SysLog::getResult, dto.getResult().trim().toUpperCase());
        }

        // IP：模糊匹配
        if (dto.getIp() != null && !dto.getIp().isBlank()) {
            wrapper.like(SysLog::getIp, dto.getIp().trim());
        }

        // 时间范围
        if (dto.getStartTime() != null && !dto.getStartTime().isBlank()) {
            wrapper.ge(SysLog::getCreateTime, LocalDateTime.parse(dto.getStartTime(), FORMATTER));
        }
        if (dto.getEndTime() != null && !dto.getEndTime().isBlank()) {
            wrapper.le(SysLog::getCreateTime, LocalDateTime.parse(dto.getEndTime(), FORMATTER));
        }

        // 排序
        boolean isAsc = "asc".equalsIgnoreCase(dto.getSortOrder());
        wrapper.orderByDesc(SysLog::getCreateTime);

        IPage<SysLog> result = sysLogMapper.selectPage(page, wrapper);
        List<SysLogVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
    }

    @Override
    public SysLogVO getById(Long id) {
        SysLog log = sysLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("日志不存在");
        }
        return convertToVO(log);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        SysLog log = sysLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("日志不存在");
        }
        sysLogMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        sysLogMapper.deleteBatchIds(ids);
    }

    private SysLogVO convertToVO(SysLog log) {
        SysLogVO vo = new SysLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setRealName(log.getRealName());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setTarget(log.getTarget());
        vo.setTargetId(log.getTargetId());
        vo.setRequestMethod(log.getRequestMethod());
        vo.setRequestUrl(log.getRequestUrl());
        vo.setResult(log.getResult());
        vo.setCostTime(log.getCostTime());
        vo.setIp(log.getIp());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }
}
