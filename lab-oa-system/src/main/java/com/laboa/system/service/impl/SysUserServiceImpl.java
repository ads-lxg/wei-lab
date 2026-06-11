package com.laboa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
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
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        UserVO userVO = convertToVO(user);
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
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
        user.setStatus(1);
        sysUserMapper.insert(user);
        assignDefaultStudentRole(user.getId());
    }

    private void assignDefaultStudentRole(Long userId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(3L);
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
    public void updateStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
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

    private UserVO convertToVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
        vo.setRoles(roles);
        return vo;
    }
}