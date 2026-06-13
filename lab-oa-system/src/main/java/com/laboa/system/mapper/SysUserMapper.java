package com.laboa.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_code FROM sys_role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("SELECT DISTINCT u.id FROM sys_user u " +
            "INNER JOIN user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE u.deleted = 0 AND r.deleted = 0 AND r.role_code != 'guest'")
    List<Long> selectNonGuestUserIds();
}