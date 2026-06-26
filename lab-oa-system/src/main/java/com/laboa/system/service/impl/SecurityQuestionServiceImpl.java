package com.laboa.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laboa.common.exception.BusinessException;
import com.laboa.system.entity.SecurityQuestion;
import com.laboa.system.mapper.SecurityQuestionMapper;
import com.laboa.system.service.SecurityQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecurityQuestionServiceImpl implements SecurityQuestionService {

    private final SecurityQuestionMapper securityQuestionMapper;

    @Override
    public List<SecurityQuestion> listAll() {
        return securityQuestionMapper.selectList(null);
    }

    @Override
    public void save(SecurityQuestion question) {
        securityQuestionMapper.insert(question);
    }

    @Override
    @Transactional
    public void update(SecurityQuestion question) {
        if (question.getId() == null) {
            throw new BusinessException("问题ID不能为空");
        }
        SecurityQuestion exist = securityQuestionMapper.selectById(question.getId());
        if (exist == null) {
            throw new BusinessException("问题不存在");
        }
        LambdaUpdateWrapper<SecurityQuestion> wrapper = new LambdaUpdateWrapper<SecurityQuestion>()
                .eq(SecurityQuestion::getId, question.getId())
                .set(SecurityQuestion::getQuestion, question.getQuestion())
                .set(SecurityQuestion::getAnswer, question.getAnswer())
                .set(SecurityQuestion::getUpdateTime, LocalDateTime.now());
        // 仅当传入非null的status时才更新状态（避免编辑问题时误将status置为null）
        if (question.getStatus() != null) {
            wrapper.set(SecurityQuestion::getStatus, question.getStatus());
        }
        securityQuestionMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SecurityQuestion exist = securityQuestionMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("问题不存在");
        }
        securityQuestionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的题目");
        }
        securityQuestionMapper.deleteBatchIds(ids);
    }

    @Override
    public List<SecurityQuestion> getRandomQuestions(int n) {
        List<SecurityQuestion> questions = securityQuestionMapper.selectRandomQuestions(n);
        // 清除答案字段，返回到前端时不暴露答案
        questions.forEach(q -> q.setAnswer(null));
        return questions;
    }

    @Override
    public boolean verifyAnswers(Map<String, String> questionAnswers) {
        if (questionAnswers == null || questionAnswers.isEmpty()) return false;
        for (Map.Entry<String, String> entry : questionAnswers.entrySet()) {
            Long questionId;
            try {
                questionId = Long.valueOf(entry.getKey());
            } catch (NumberFormatException e) {
                return false;
            }
            SecurityQuestion question = securityQuestionMapper.selectById(questionId);
            if (question == null || question.getAnswer() == null) return false;
            // 忽略大小写和首尾空格
            String correctAnswer = question.getAnswer().trim().toLowerCase();
            String userAnswer = entry.getValue() != null ? entry.getValue().trim().toLowerCase() : "";
            if (!correctAnswer.equals(userAnswer)) {
                return false;
            }
        }
        return true;
    }
}
