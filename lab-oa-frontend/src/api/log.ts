import { get, del } from './request'
import type { PageResult, SysLogVO, SysLogSearchDTO } from '@/types'

/** GET /api/admin/log/page */
export function getLogPage(params: SysLogSearchDTO): Promise<PageResult<SysLogVO>> {
  return get<PageResult<SysLogVO>>('/admin/log/page', params as unknown as Record<string, unknown>)
}

/** GET /api/admin/log/{id} */
export function getLogDetail(id: number): Promise<SysLogVO> {
  return get<SysLogVO>(`/admin/log/${id}`)
}

/** DELETE /api/admin/log/{id} */
export function deleteLog(id: number): Promise<void> {
  return del<void>(`/admin/log/${id}`)
}

/** DELETE /api/admin/log/batch */
export function batchDeleteLogs(ids: number[]): Promise<void> {
  return del<void>('/admin/log/batch', { data: ids })
}
