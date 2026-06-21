package com.laboa.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码ID（失败3次后需要） */
    private String captchaId;

    /** 验证码答案 */
    private String captchaAnswer;

    /** 设备类型: web / mobile / desktop */
    private String deviceType;
}
