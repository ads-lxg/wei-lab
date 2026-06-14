package com.laboa.literature.service;

import com.laboa.literature.entity.Literature;

public interface LiteratureService {

    Literature getById(Long id);

    void recordDownload(Long literatureId, Long userId);
}