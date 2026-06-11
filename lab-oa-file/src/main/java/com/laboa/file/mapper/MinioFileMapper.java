package com.laboa.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.file.entity.MinioFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MinioFileMapper extends BaseMapper<MinioFile> {
}