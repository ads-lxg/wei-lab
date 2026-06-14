import { get, post, put, del } from './request'
import type { UserVO, UserPageDTO, PageResult } from '@/types'

/** GET /api/admin/user/page */
export function getUserPage(params: UserPageDTO): Promise<PageResult<UserVO>> {
  return get<PageResult<UserVO>>('/admin/user/page', params as unknown as Record<string, unknown>)
}

/** GET /api/admin/user/{id}/detail */
export function getUserDetail(id: number): Promise<UserVO> {
  return get<UserVO>(`/admin/user/${id}/detail`)
}

/** PUT /api/admin/user/{id}/status */
export function updateUserStatus(id: number, status: number): Promise<void> {
  return put<void>(`/admin/user/${id}/status`, null, { params: { status } })
}

/** PUT /api/admin/user/{id}/roles */
export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return put<void>(`/admin/user/${id}/roles`, roleIds)
}

/** PUT /api/admin/user/{id}/info */
export function updateUserInfo(id: number, data: Record<string, unknown>): Promise<void> {
  return put<void>(`/admin/user/${id}/info`, data)
}

/** DELETE /api/admin/user/{id} */
export function deleteUser(id: number): Promise<void> {
  return del<void>(`/admin/user/${id}`)
}

/** POST /api/admin/user/{id}/avatar */
export function adminUploadAvatar(id: number, file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  return post<string>(`/admin/user/${id}/avatar`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** DELETE /api/admin/user/{id}/avatar */
export function adminDeleteAvatar(id: number): Promise<void> {
  return del<void>(`/admin/user/${id}/avatar`)
}
