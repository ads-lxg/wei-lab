import { post } from './request'

/** POST /api/admin/data-cleanup/execute - 手动执行数据清理 */
export function executeDataCleanup(): Promise<Record<string, number>> {
  return post<Record<string, number>>('/admin/data-cleanup/execute')
}
