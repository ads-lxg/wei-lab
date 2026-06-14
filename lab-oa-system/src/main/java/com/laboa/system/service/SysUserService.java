package com.laboa.system.service;

import com.laboa.common.result.PageResult;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.dto.UserPageDTO;
import com.laboa.system.entity.SysUser;
import com.laboa.system.vo.LoginVO;
import com.laboa.system.vo.UserVO;

import java.util.List;

public interface SysUserService {

    LoginVO login(LoginDTO dto);

    void register(RegisterDTO dto);

    UserVO getById(Long id);

    PageResult<UserVO> page(UserPageDTO dto);

    void updateStatus(Long id, Integer status);

    void assignRoles(Long userId, List<Long> roleIds);

    /** 编辑用户信息（真实姓名、手机、邮箱等） */
    void updateUserInfo(Long id, SysUser updateData);

    /** 删除用户（逻辑删除） */
    void deleteUser(Long id);
}