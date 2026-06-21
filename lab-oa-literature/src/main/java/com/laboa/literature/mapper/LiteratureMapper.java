package com.laboa.literature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laboa.literature.entity.Literature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface LiteratureMapper extends BaseMapper<Literature> {

    /**
     * 查询回收站文献（分页）- 绕过逻辑删除查询deleted=1的记录
     */
    @Select("SELECT * FROM literature WHERE deleted = 1 ORDER BY update_time DESC")
    IPage<Literature> selectRecyclePage(Page<Literature> page);

    /**
     * 根据ID查询回收站中的文献 - 绕过逻辑删除
     */
    @Select("SELECT * FROM literature WHERE id = #{id} AND deleted = 1")
    Literature selectRecycleById(@Param("id") Long id);

    /**
     * 恢复文献 - 将deleted设为0
     */
    @Update("UPDATE literature SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int recoverById(@Param("id") Long id);

    /**
     * 物理删除 - 真正从数据库删除记录
     */
    @Delete("DELETE FROM literature WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 查询所有已逻辑删除的文献ID - 用于搜索时排除
     */
    @Select("SELECT id FROM literature WHERE deleted = 1")
    java.util.List<Long> selectDeletedIds();
}