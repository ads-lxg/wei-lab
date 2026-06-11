package com.laboa.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String phone;

    private String realName;

    private String avatar;

    private Integer status;

    private List<String> roles;

    private LocalDateTime createTime;
}