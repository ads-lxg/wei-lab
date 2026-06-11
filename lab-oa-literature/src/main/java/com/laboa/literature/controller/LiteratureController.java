package com.laboa.literature.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.laboa.common.constant.Constants;
import com.laboa.common.exception.BusinessException;
import com.laboa.common.result.PageResult;
import com.laboa.common.result.Result;
import com.laboa.file.service.FileService;
import com.laboa.literature.dto.LiteraturePageDTO;
import com.laboa.literature.entity.Literature;
import com.laboa.literature.service.LiteratureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/literature")
@RequiredArgsConstructor
public class LiteratureController {

    private final LiteratureService literatureService;
    private final FileService fileService;

    @GetMapping
    public Result<PageResult<Literature>> page(@ModelAttribute LiteraturePageDTO dto) {
        PageResult<Literature> pageResult = literatureService.page(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<Literature> getById(@PathVariable("id") Long id) {
        Literature literature = literatureService.getById(id);
        return Result.success(literature);
    }

    @PostMapping
    public Result<Literature> create(@RequestPart("literature") Literature literature,
                                     @RequestPart("file") MultipartFile file) {
        Long uploaderId = StpUtil.getLoginIdAsLong();
        Literature result = literatureService.create(literature, file, uploaderId);
        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @RequestBody Literature literature) {
        literature.setId(id);
        literatureService.update(literature);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        literatureService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/download")
    public Result<String> download(@PathVariable("id") Long id) {
        if (StpUtil.hasRole(Constants.ROLE_GUEST)) {
            throw new BusinessException("游客无权下载文献");
        }
        Literature literature = literatureService.getById(id);
        Long userId = StpUtil.getLoginIdAsLong();
        literatureService.recordDownload(id, userId);
        String presignedUrl = fileService.getPresignedUrl(literature.getFileId());
        return Result.success(presignedUrl);
    }
}