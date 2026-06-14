import { get, put } from './request'
import type { Notification, PageResult } from '@/types'

/** GET /api/notification - list notifications */
export function getNotifications(
  isRead?: number,
  page: number = 1,
  size: number = 10,
): Promise<PageResult<Notification>> {
  return get<PageResult<Notification>>('/notification', { isRead, page, size } as unknown as Record<string, unknown>)
}

/** PUT /api/notification/{id}/read */
export function markAsRead(id: number): Promise<void> {
  return put<void>(`/notification/${id}/read`)
}

/** PUT /api/notification/read-all */
export function markAllAsRead(): Promise<void> {
  return put<void>('/notification/read-all')
}

/** GET /api/notification/unread-count */
export function getUnreadCount(): Promise<number> {
  return get<number>('/notification/unread-count')
}
