import { get, post, put, del } from './request'
import type { PageResult } from '@/types'

// ===== /api/doc/* =====

export interface MdDocument {
  id: number
  fileId: number
  title: string
  authorId: number
  fileType: string
  status: number
  createTime: string
  updateTime: string
}

export interface DocPageDTO {
  page?: number
  size?: number
  keyword?: string
  authorId?: number
}

/** POST /api/doc */
export function createDoc(title: string, file: File): Promise<MdDocument> {
  const fd = new FormData()
  fd.append('title', title)
  fd.append('file', file)
  return post<MdDocument>('/doc', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

/** POST /api/doc/online */
export function createOnlineDoc(data: { title: string; content: string; fileType?: string }): Promise<MdDocument> {
  return post<MdDocument>('/doc/online', data)
}

/** GET /api/doc/page */
export function getDocPage(params: DocPageDTO): Promise<PageResult<MdDocument>> {
  return get<PageResult<MdDocument>>('/doc/page', params as Record<string, unknown>)
}

/** GET /api/doc/{id} */
export function getDocById(id: number | string): Promise<{ doc: MdDocument; url: string; content: string }> {
  return get<{ doc: MdDocument; url: string; content: string }>(`/doc/${id}`)
}

/** PUT /api/doc/{id} */
export function updateDoc(id: number, title?: string, file?: File): Promise<void> {
  const fd = new FormData()
  if (title) fd.append('title', title)
  if (file) fd.append('file', file)
  return put<void>(`/doc/${id}`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** DELETE /api/doc/{id} */
export function deleteDoc(id: number): Promise<void> {
  return del<void>(`/doc/${id}`)
}

/** GET /api/doc/search */
export function searchDocs(keyword: string, page = 1, size = 10): Promise<any> {
  return get('/doc/search', { keyword, page, size } as Record<string, unknown>)
}

/** PUT /api/doc/{id}/status */
export function updateDocStatus(id: number, status: number): Promise<void> {
  return put<void>(`/doc/${id}/status`, null, { params: { status } })
}
