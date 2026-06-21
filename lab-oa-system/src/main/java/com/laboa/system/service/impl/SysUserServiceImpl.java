package com.laboa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.file.entity.MinioFile;
import com.laboa.file.service.FileService;
import org.springframework.dao.DuplicateKeyException;
import com.laboa.security.util.JwtUtil;
import com.laboa.system.dto.LoginDTO;
import com.laboa.system.dto.RegisterDTO;
import com.laboa.system.dto.UserPageDTO;
import com.laboa.system.entity.SysUser;
import com.laboa.system.entity.UserRole;
import com.laboa.system.mapper.SysUserMapper;
import com.laboa.system.mapper.UserRoleMapper;
import com.laboa.system.service.SysUserService;
import com.laboa.system.vo.LoginVO;
import com.laboa.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileService fileService;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user;
        try {
            user = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())
            );
        } catch (Exception e) {
            log.error("登录查询失败: username={}, error={}", dto.getUsername(), e.getMessage(), e);
            throw new BusinessException("登录查询失败: " + e.getClass().getSimpleName() + " - " + (e.getMessage() != null ? e.getMessage() : "未知错误，请查看控制台 'Caused by'"));
        }
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        // token 由 AuthController 通过 StpUtil.login() 生成
        UserVO userVO = convertToVO(user);
        LoginVO loginVO = new LoginVO();
        loginVO.setUser(userVO);
        return loginVO;
    }

    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        if (sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, dto.getEmail())
        ) > 0) {
            throw new BusinessException("邮箱已被注册");
        }
        if (sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())
        ) > 0) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setStatus(1);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("uk_email")) {
                throw new BusinessException("邮箱已被注册");
            }
            if (msg != null && msg.contains("uk_username")) {
                throw new BusinessException("用户名已存在");
            }
            throw new BusinessException("账号信息重复，请更换邮箱或用户名");
        }
        // 默认注册为游客，需管理员提升为 student / teacher
        assignDefaultGuestRole(user.getId());
    }

    private void assignDefaultGuestRole(Long userId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(4L); // guest
        userRoleMapper.insert(userRole);
    }

    @Override
    public UserVO getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return convertToVO(user);
    }

    @Override
    public PageResult<UserVO> page(UserPageDTO dto) {
        Page<SysUser> page = new Page<>(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, dto.getKeyword())
                    .or()
                    .like(SysUser::getEmail, dto.getKeyword())
                    .or()
                    .like(SysUser::getRealName, dto.getKeyword())
            );
        }
        if (dto.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = sysUserMapper.selectPage(page, wrapper);
        List<UserVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), voList);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        sysUserMapper.update(null,
                new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, id)
                        .set(SysUser::getStatus, status)
                        .set(SysUser::getUpdateTime, java.time.LocalDateTime.now())
        );
    }

    /** ADMIN角色ID和数量上限 */
    private static final long ADMIN_ROLE_ID = 1L;
    private static final int MAX_ADMIN_COUNT = 2;

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 检查是否包含ADMIN角色
        boolean assignAdmin = roleIds != null && roleIds.contains(ADMIN_ROLE_ID);

        if (assignAdmin) {
            // 获取当前操作用户
            Long currentUserId = Long.valueOf(StpUtil.getLoginIdAsLong());

            // 禁止管理员为自己设置角色（防止误操作导致无管理员）
            if (currentUserId.equals(userId)) {
                throw new BusinessException("不允许为自己设置管理角色");
            }

            // 检查管理员数量是否已达上限
            int currentAdminCount = sysUserMapper.countUsersByRoleId(ADMIN_ROLE_ID);
            // 如果目标用户当前不是管理员，分配后将增加一位管理员
            List<String> targetRoles = sysUserMapper.selectRoleCodesByUserId(userId);
            boolean targetIsAdmin = targetRoles.contains("admin");
            int newAdminCount = targetIsAdmin ? currentAdminCount : currentAdminCount + 1;
            if (newAdminCount > MAX_ADMIN_COUNT) {
                throw new BusinessException("管理员数量已满（最多" + MAX_ADMIN_COUNT + "个）");
            }
        }

        LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(deleteWrapper);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream().map(roleId -> {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                return ur;
            }).collect(Collectors.toList());
            for (UserRole userRole : userRoles) {
                userRoleMapper.insert(userRole);
            }
        }
    }

    @Override
    @Transactional
    public void updateUserInfo(Long id, SysUser updateData) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        var wrapper = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, id);
        boolean hasUpdate = false;
        if (updateData.getRealName() != null && !updateData.getRealName().isBlank()) {
            wrapper.set(SysUser::getRealName, updateData.getRealName());
            hasUpdate = true;
        }
        if (updateData.getPhone() != null && !updateData.getPhone().isBlank()) {
            wrapper.set(SysUser::getPhone, updateData.getPhone());
            hasUpdate = true;
        }
        if (updateData.getEmail() != null && !updateData.getEmail().isBlank()) {
            wrapper.set(SysUser::getEmail, updateData.getEmail());
            hasUpdate = true;
        }
        if (hasUpdate) {
            wrapper.set(SysUser::getUpdateTime, java.time.LocalDateTime.now());
            sysUserMapper.update(null, wrapper);
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 检查是否是最后一个 admin
        List<Long> adminIds = sysUserMapper.selectNonGuestUserIds();
        long adminCount = adminIds.stream().filter(uid -> {
            List<String> roles = sysUserMapper.selectRoleCodesByUserId(uid);
            return roles.contains("admin");
        }).count();
        List<String> userRoles = sysUserMapper.selectRoleCodesByUserId(id);
        if (userRoles.contains("admin") && adminCount <= 1) {
            throw new BusinessException("不能删除最后一个管理员");
        }
        // 逻辑删除用户
        sysUserMapper.deleteById(id);
        // 删除用户的角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
    }

    // ==================== 头像 ====================

    @Override
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 删除旧头像文件
        if (user.getAvatar() != null && !user.getAvatar().isBlank()) {
            try {
                fileService.deleteFile(Long.valueOf(user.getAvatar()));
            } catch (Exception e) {
                log.warn("删除旧头像文件失败: avatarId={}, error={}", user.getAvatar(), e.getMessage());
            }
        }
        // 上传新头像到 MinIO
        MinioFile minioFile = fileService.uploadFile(file, userId);
        // 保存头像文件 ID
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getAvatar, String.valueOf(minioFile.getId()))
                .set(SysUser::getUpdateTime, java.time.LocalDateTime.now()));
        return "/api/file/" + minioFile.getId() + "/stream";
    }

    @Override
    @Transactional
    public void deleteAvatar(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getAvatar() == null || user.getAvatar().isBlank()) {
            return;
        }
        // 删除 MinIO 文件
        try {
            fileService.deleteFile(Long.valueOf(user.getAvatar()));
        } catch (Exception e) {
            log.warn("删除头像文件失败: avatarId={}", user.getAvatar(), e);
        }
        // 清空头像字段
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getAvatar, null)
                .set(SysUser::getUpdateTime, java.time.LocalDateTime.now()));
    }

    @Override
    public String getAvatarUrl(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isBlank()) {
            return null;
        }
        try {
            return "/api/file/" + user.getAvatar() + "/stream";
        } catch (Exception e) {
            log.warn("获取头像预签名URL失败: userId={}, avatarId={}", userId, user.getAvatar(), e);
            return null;
        }
    }

    @Override
    public boolean verifyPassword(Long userId, String password) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return false;
        return passwordEncoder.matches(password, user.getPassword());
    }

    // ==================== 私有方法 ====================

    private UserVO convertToVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        // 头像: fileId → 代理URL
        if (user.getAvatar() != null && !user.getAvatar().isBlank()) {
            vo.setAvatar("/api/file/" + user.getAvatar() + "/stream");
        }
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        vo.setRoles(roles);
        return vo;
    }
}