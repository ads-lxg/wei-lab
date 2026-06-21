package com.laboa.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.system.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
