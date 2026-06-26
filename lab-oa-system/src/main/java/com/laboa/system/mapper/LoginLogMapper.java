package com.laboa.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.system.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 统计最近24小时内活跃登录的不同IP数
     * 只统计最新操作为LOGIN的IP（已登出的IP不计入）
     */
    @Select("SELECT COUNT(DISTINCT l1.ip) FROM login_log l1 " +
            "WHERE l1.user_id = #{userId} " +
            "  AND l1.action = 'LOGIN' " +
            "  AND l1.create_time >= #{since} " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM login_log l2 " +
            "    WHERE l2.user_id = l1.user_id " +
            "      AND l2.ip = l1.ip " +
            "      AND l2.action = 'LOGOUT' " +
            "      AND l2.create_time > l1.create_time" +
            "  )")
    long countActiveDistinctIps(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);
}
