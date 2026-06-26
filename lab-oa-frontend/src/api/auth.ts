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

/** GET /api/user/captcha/check - 检查是否需要验证码 */
export function checkCaptcha(username: string): Promise<{ needCaptcha: boolean; captchaId?: string; targetX?: number }> {
  return get('/user/captcha/check', { username })
}

/** POST /api/user/verify-password - 验证当前用户密码（二次验证） */
export function verifyPassword(password: string): Promise<void> {
  return post<void>('/user/verify-password', { password })
}

/** POST /api/user/logout - 退出登录 */
export function logout(): Promise<void> {
  return post<void>('/user/logout')
}
