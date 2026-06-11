package com.laboa.literature.service;

import com.laboa.common.result.PageResult;
import com.laboa.literature.dto.LiteraturePageDTO;
import com.laboa.literature.entity.Literature;
import org.springframework.web.multipart.MultipartFile;

public interface LiteratureService {

    Literature create(Literature literature, MultipartFile file, Long uploaderId);

    PageResult<Literature> page(LiteraturePageDTO dto);

    Literature getById(Long id);

    void update(Literature literature);

    void delete(Long id);

    void incrementViewCount(Long id);

    void recordDownload(Long literatureId, Long userId);
}