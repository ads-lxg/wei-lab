package com.laboa.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role_permission")
public class RolePermission {

    private Long roleId;

    private Long permId;
}