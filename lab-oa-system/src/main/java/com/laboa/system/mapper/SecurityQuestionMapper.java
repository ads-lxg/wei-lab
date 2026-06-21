package com.laboa.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laboa.system.entity.SecurityQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SecurityQuestionMapper extends BaseMapper<SecurityQuestion> {

    /** 随机抽取N道启用的题目 */
    @Select("SELECT id, question, answer FROM security_question WHERE status = 1 AND deleted = 0 ORDER BY RAND() LIMIT #{n}")
    List<SecurityQuestion> selectRandomQuestions(int n);
}
