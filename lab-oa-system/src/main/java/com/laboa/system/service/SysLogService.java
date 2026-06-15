package com.laboa.system.service;

import com.laboa.common.result.PageResult;
import com.laboa.system.dto.SysLogSearchDTO;
import com.laboa.system.vo.SysLogVO;

public interface SysLogService {

    /** 分页搜索操作日志 */
    PageResult<SysLogVO> search(SysLogSearchDTO dto);

    /** 根据ID查询日志详情 */
    SysLogVO getById(Long id);

    /** 删除指定日志 */
    void deleteById(Long id);

    /** 批量删除日志 */
    void batchDelete(java.util.List<Long> ids);
}
