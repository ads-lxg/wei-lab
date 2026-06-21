import { get, post, put, del, postForm } from './request'
import axios from 'axios'
import { getToken } from '@/utils/token'
import type {
  PageResult,
  LiteratureListItemVO,
  LiteratureDetailVO,
  LiteratureRecycleVO,
  DocumentFolderQueryDTO,
  DocumentSearchDTO,
  DocumentBatchDeleteDTO,
  DocumentBatchMoveDTO,
  DocumentBatchRecoverDTO,
  BatchUploadResultVO,
} from '@/types'

// ===== Upload =====

/** POST /api/document/upload */
export function uploadDocument(formData: FormData): Promise<LiteratureDetailVO> {
  return postForm<LiteratureDetailVO>('/document/upload', formData)
}

/** POST /api/document/batch-upload */
export function batchUploadDocuments(formData: FormData): Promise<BatchUploadResultVO> {
  return postForm<BatchUploadResultVO>('/document/batch-upload', formData)
}

// ===== Query =====

/** GET /api/document/folder */
export function listByFolder(params: DocumentFolderQueryDTO): Promise<PageResult<LiteratureListItemVO>> {
  return get<PageResult<LiteratureListItemVO>>('/document/folder', params as unknown as Record<string, unknown>)
}

/** GET /api/document/{id}/detail */
export function getDocumentDetail(id: number | string): Promise<LiteratureDetailVO> {
  return get<LiteratureDetailVO>(`/document/${id}/detail`)
}

/** GET /api/document/search */
export function searchDocuments(params: DocumentSearchDTO): Promise<PageResult<LiteratureListItemVO>> {
  return get<PageResult<LiteratureListItemVO>>('/document/search', params as unknown as Record<string, unknown>)
}

/** GET /api/document/dashboard-stats */
export function getDashboardStats(): Promise<{
  totalDocuments: number
  todayNewDocuments: number
  totalDownloads: number
  hotDocuments: LiteratureListItemVO[]
  recentUploads: LiteratureListItemVO[]
}> {
  return get('/document/dashboard-stats')
}

// ===== Download =====

/** GET /api/document/{id}/download - returns proxy URL with download=true */
export function getDownloadUrl(id: number | string): Promise<string> {
  return get<string>(`/document/${id}/download`)
}

/** GET /api/document/{id}/preview - returns proxy URL for inline preview */
export function getPreviewUrl(id: number | string): Promise<string> {
  return get<string>(`/document/${id}/preview`)
}

/** POST /api/document/batch-download - triggers file download directly */
export function batchDownload(ids: DocumentBatchDeleteDTO): Promise<void> {
  return post<void>('/document/batch-download', ids, { responseType: 'blob' })
}

// ===== Delete / Recycle =====

/** DELETE /api/document/{id} */
export function deleteDocument(id: number | string): Promise<void> {
  return del<void>(`/document/${id}`)
}

/** DELETE /api/document/{id}/permanent — 回收站彻底删除 */
export function permanentDeleteDocument(id: number | string): Promise<void> {
  return del<void>(`/document/${id}/permanent`)
}

/** DELETE /api/document/batch-permanent — 批量彻底删除 */
export function batchPermanentDeleteDocuments(data: DocumentBatchDeleteDTO): Promise<void> {
  return del<void>('/document/batch-permanent', { data })
}

/** DELETE /api/document/batch-delete */
export function batchDeleteDocuments(data: DocumentBatchDeleteDTO): Promise<void> {
  return del<void>('/document/batch-delete', { data })
}

// ===== Move =====

/** PUT /api/document/move */
export function moveDocument(documentId: number, targetFolderId: number): Promise<void> {
  return put<void>('/document/move', { documentId, targetFolderId })
}

/** PUT /api/document/batch-move */
export function batchMoveDocuments(data: DocumentBatchMoveDTO): Promise<void> {
  return put<void>('/document/batch-move', data)
}

// ===== Recycle Bin =====

/** GET /api/document/recycle-bin */
export function listRecycleBin(page: number = 1, size: number = 10): Promise<PageResult<LiteratureRecycleVO>> {
  return get<PageResult<LiteratureRecycleVO>>('/document/recycle-bin', { page, size } as unknown as Record<string, unknown>)
}

/** PUT /api/document/{id}/recover */
export function recoverDocument(id: number | string): Promise<void> {
  return put<void>(`/document/${id}/recover`)
}

/** PUT /api/document/batch-recover */
export function batchRecoverDocuments(data: DocumentBatchRecoverDTO): Promise<void> {
  return put<void>('/document/batch-recover', data)
}

// ===== DOI 批量解析 =====

/** POST /api/document/batch-parse-doi — 上传DOI Excel，下载填充好的批量上传Excel */
export async function batchParseDoi(file: File): Promise<void> {
  const fd = new FormData()
  fd.append('file', file)
  const token = getToken()
  const resp = await axios.post('/api/document/batch-parse-doi', fd, {
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(token ? { Authorization: token } : {}),
    },
    responseType: 'blob',
    timeout: 120000,
  })
  const url = window.URL.createObjectURL(new Blob([resp.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = 'doi_import_result.xlsx'
  a.click()
  window.URL.revokeObjectURL(url)
}
