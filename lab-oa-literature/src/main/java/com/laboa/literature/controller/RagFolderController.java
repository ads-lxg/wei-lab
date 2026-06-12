package com.laboa.literature.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.Result;
import com.laboa.literature.dto.FolderCreateDTO;
import com.laboa.literature.dto.FolderMoveDTO;
import com.laboa.literature.dto.FolderUpdateDTO;
import com.laboa.literature.service.RagFolderService;
import com.laboa.literature.vo.FolderTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文献目录树管理接口
 * <p>
 * 查询目录树：所有登录用户均可访问<br>
 * 增删改移动：仅管理员可操作
 */
@Tag(name = "文献目录管理", description = "文献目录树的增删改查和移动操作")
@RestController
@RequestMapping("/api/rag/folder")
@RequiredArgsConstructor
public class RagFolderController {

    private final RagFolderService ragFolderService;

    @Operation(summary = "查询目录树", description = "一次性返回完整目录树结构，所有登录用户均可访问")
    @GetMapping("/tree")
    public Result<List<FolderTreeVO>> tree() {
        checkLogin();
        List<FolderTreeVO> tree = ragFolderService.tree();
        return Result.success(tree);
    }

    @Operation(summary = "创建目录", description = "在指定父目录下创建子目录，自动生成path和层级")
    @PostMapping("/create")
    public Result<Long> create(@Valid @RequestBody FolderCreateDTO dto) {
        checkAdmin();
        Long id = ragFolderService.create(dto);
        return Result.success(id);
    }

    @Operation(summary = "修改目录名称", description = "修改指定目录的名称，同级不可重名")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody FolderUpdateDTO dto) {
        checkAdmin();
        ragFolderService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除目录", description = "逻辑删除目录，存在子目录时禁止删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "目录ID", required = true) @PathVariable("id") Long id) {
        checkAdmin();
        ragFolderService.delete(id);
        return Result.success();
    }

    @Operation(summary = "移动目录", description = "将目录移动到新的父节点下，自动递归更新所有子节点路径")
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody FolderMoveDTO dto) {
        checkAdmin();
        ragFolderService.move(dto);
        return Result.success();
    }

    /**
     * 校验已登录（任何角色均可）
     */
    private void checkLogin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
    }

    /**
     * 校验管理员权限，非管理员直接抛出异常
     */
    private void checkAdmin() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(401, "请先登录");
        }
        if (!StpUtil.hasRole("admin")) {
            throw new BusinessException(403, "仅管理员可操作目录树");
        }
    }
}
