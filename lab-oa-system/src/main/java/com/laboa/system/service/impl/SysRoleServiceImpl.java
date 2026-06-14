package com.laboa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laboa.common.exception.BusinessException;
import com.laboa.system.entity.SysRole;
import com.laboa.system.mapper.SysRoleMapper;
import com.laboa.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;

    @Override
    public List<SysRole> listAll() {
        return sysRoleMapper.selectList(null);
    }

    @Override
    public void save(SysRole role) {
        sysRoleMapper.insert(role);
    }

    @Override
    @Transactional
    public void update(SysRole role) {
        if (role.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRole exist = sysRoleMapper.selectById(role.getId());
        if (exist == null) {
            throw new BusinessException("角色不存在");
        }
        // 用 LambdaUpdateWrapper 精确更新，避免 @TableLogic 字段干扰
        sysRoleMapper.update(null,
                new LambdaUpdateWrapper<SysRole>()
                        .eq(SysRole::getId, role.getId())
                        .set(SysRole::getRoleCode, role.getRoleCode())
                        .set(SysRole::getRoleName, role.getRoleName())
                        .set(SysRole::getDescription, role.getDescription())
                        .set(SysRole::getUpdateTime, LocalDateTime.now())
        );
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 保护内置角色（ID 1-4）
        if (id >= 1 && id <= 4) {
            throw new BusinessException("内置角色不允许删除");
        }
        SysRole exist = sysRoleMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("角色不存在");
        }
        sysRoleMapper.deleteById(id);
    }
}