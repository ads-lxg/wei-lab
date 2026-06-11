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
}