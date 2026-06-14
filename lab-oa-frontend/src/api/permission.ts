import { get, put } from './request'
import type { SysPermission } from '@/types'

/** GET /api/admin/permission/list */
export function listAllPermissions(): Promise<SysPermission[]> {
  return get<SysPermission[]>('/admin/permission/list')
}

/** GET /api/admin/permission/role/{roleId} */
export function getRolePermissions(roleId: number): Promise<number[]> {
  return get<number[]>(`/admin/permission/role/${roleId}`)
}

/** PUT /api/admin/permission/role/{roleId} */
export function setRolePermissions(roleId: number, permIds: number[]): Promise<void> {
  return put<void>(`/admin/permission/role/${roleId}`, permIds)
}
