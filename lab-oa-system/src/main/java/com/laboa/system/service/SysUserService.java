package com.laboa.system.service;

import com.laboa.common.result.PageResult;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.dto.UserPageDTO;
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
}