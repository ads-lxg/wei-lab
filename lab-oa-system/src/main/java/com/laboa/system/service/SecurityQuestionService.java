package com.laboa.system.service;

import com.laboa.system.entity.SecurityQuestion;

import java.util.List;
import java.util.Map;

public interface SecurityQuestionService {

    List<SecurityQuestion> listAll();

    void save(SecurityQuestion question);

    void update(SecurityQuestion question);

    void delete(Long id);

    /** 批量删除 */
    void batchDelete(List<Long> ids);

    /** 随机抽取N道题目（只返回题目，不返回答案） */
    List<SecurityQuestion> getRandomQuestions(int n);

    /** 验证答案：传入问题ID(字符串key)和答案的映射，返回是否正确 */
    boolean verifyAnswers(Map<String, String> questionAnswers);
}
