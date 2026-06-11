package com.laboa.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.common.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}