package com.laboa.system.service;

import com.laboa.common.result.PageResult;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.dto.UserPageDTO;
import com.laboa.system.entity.SysUser;
import com.laboa.system.vo.LoginVO;
import com.laboa.system.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

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

    /** 上传头像，返回头像预览 URL */
    String uploadAvatar(Long userId, MultipartFile file);

    /** 删除头像，恢复默认 */
    void deleteAvatar(Long userId);

    /** 获取头像 URL（用户无头像时返回 null） */
    String getAvatarUrl(Long userId);
}