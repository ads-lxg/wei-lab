package com.laboa.system.service;

import com.laboa.system.entity.SysRole;

import java.util.List;

public interface SysRoleService {

    List<SysRole> listAll();

    void save(SysRole role);

    void update(SysRole role);

    void delete(Long id);
}