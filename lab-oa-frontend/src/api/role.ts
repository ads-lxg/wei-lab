import { get, post, put, del } from './request'
import type { SysRole } from '@/types'

/** GET /api/admin/role/list */
export function listAllRoles(): Promise<SysRole[]> {
  return get<SysRole[]>('/admin/role/list')
}

/** POST /api/admin/role */
export function createRole(data: Partial<SysRole>): Promise<void> {
  return post<void>('/admin/role', data)
}

/** PUT /api/admin/role/{id} */
export function updateRole(id: number, data: Partial<SysRole>): Promise<void> {
  return put<void>(`/admin/role/${id}`, data)
}

/** DELETE /api/admin/role/{id} */
export function deleteRole(id: number): Promise<void> {
  return del<void>(`/admin/role/${id}`)
}
