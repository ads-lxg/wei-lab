import { get, post, put, del } from './request'

// ===== 公开接口（注册时使用） =====

/** GET /api/user/security-questions - 获取两道随机安全问题 */
export function getRandomQuestions(): Promise<Array<{ id: number; question: string }>> {
  return get('/user/security-questions')
}

/** POST /api/user/security-questions/verify - 验证安全答案 */
export function verifySecurityAnswers(answers: Record<number, string>): Promise<string> {
  return post<string>('/user/security-questions/verify', answers)
}

// ===== 管理员接口 =====

export interface SecurityQuestionVO {
  id: number
  question: string
  answer: string
  status: number
  createTime: string
  updateTime: string
}

/** GET /api/admin/security-question/list */
export function listAllQuestions(): Promise<SecurityQuestionVO[]> {
  return get<SecurityQuestionVO[]>('/admin/security-question/list')
}

/** POST /api/admin/security-question */
export function createQuestion(data: { question: string; answer: string }): Promise<void> {
  return post<void>('/admin/security-question', data)
}

/** PUT /api/admin/security-question/{id} */
export function updateQuestion(id: number, data: { question: string; answer: string; status?: number }): Promise<void> {
  return put<void>(`/admin/security-question/${id}`, data)
}

/** DELETE /api/admin/security-question/{id} */
export function deleteQuestion(id: number): Promise<void> {
  return del<void>(`/admin/security-question/${id}`)
}

/** DELETE /api/admin/security-question/batch - 批量删除 */
export function batchDeleteQuestions(ids: number[]): Promise<void> {
  return del<void>('/admin/security-question/batch', { data: ids })
}
