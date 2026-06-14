import { post, get, del } from './request'
import type { LoginDTO, LoginVO, RegisterDTO, UserVO } from '@/types'

/** POST /api/user/login */
export function login(data: LoginDTO): Promise<LoginVO> {
  return post<LoginVO>('/user/login', data)
}

/** POST /api/user/register */
export function register(data: RegisterDTO): Promise<void> {
  return post<void>('/user/register', data)
}

/** GET /api/user/info - get current user info */
export function getUserInfo(): Promise<UserVO> {
  return get<UserVO>('/user/info')
}

/** POST /api/user/avatar - upload avatar */
export function uploadAvatar(file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  return post<string>('/user/avatar', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** DELETE /api/user/avatar - delete avatar */
export function deleteAvatar(): Promise<void> {
  return del<void>('/user/avatar')
}
