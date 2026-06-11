package com.laboa.doc.service;

import com.laboa.common.result.PageResult;
import com.laboa.doc.dto.DocPageDTO;
import com.laboa.doc.entity.MdDocument;

public interface MdDocumentService {

    MdDocument create(Long fileId, String title, Long authorId, String fileType);

    PageResult<MdDocument> page(DocPageDTO dto);

    MdDocument getById(Long id);

    void update(Long id, String title, Long fileId);

    void delete(Long id);

    void updateStatus(Long id, Integer status);
}