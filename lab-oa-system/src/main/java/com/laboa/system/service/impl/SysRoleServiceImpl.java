package com.laboa.system.service.impl;

import com.laboa.common.exception.BusinessException;
import com.laboa.system.entity.SysRole;
import com.laboa.system.mapper.SysRoleMapper;
import com.laboa.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public void update(SysRole role) {
        if (role.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        SysRole exist = sysRoleMapper.selectById(role.getId());
        if (exist == null) {
            throw new BusinessException("角色不存在");
        }
        sysRoleMapper.updateById(role);
    }

    @Override
    public void delete(Long id) {
        SysRole exist = sysRoleMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("角色不存在");
        }
        sysRoleMapper.deleteById(id);
    }
}