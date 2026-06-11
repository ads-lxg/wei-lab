package com.laboa.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.literature.entity.Literature;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LiteratureMapper extends BaseMapper<Literature> {
}