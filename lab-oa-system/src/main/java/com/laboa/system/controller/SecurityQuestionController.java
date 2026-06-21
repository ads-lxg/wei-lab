package com.laboa.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.laboa.common.result.Result;
import com.laboa.system.entity.SecurityQuestion;
import com.laboa.system.service.SecurityQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SaCheckPermission("user:list")
@RestController
@RequestMapping("/api/admin/security-question")
@RequiredArgsConstructor
public class SecurityQuestionController {

    private final SecurityQuestionService securityQuestionService;

    @GetMapping("/list")
    public Result<List<SecurityQuestion>> listAll() {
        return Result.success(securityQuestionService.listAll());
    }

    @PostMapping
    public Result<Void> save(@RequestBody SecurityQuestion question) {
        securityQuestionService.save(question);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody SecurityQuestion question) {
        question.setId(id);
        securityQuestionService.update(question);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        securityQuestionService.delete(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        securityQuestionService.batchDelete(ids);
        return Result.success();
    }
}
