import { get, post, del } from './request'
import type { MinioFile } from '@/types'

// ===== /api/file/* =====

/** POST /api/file/upload */
export function uploadFile(file: File): Promise<MinioFile> {
  const fd = new FormData()
  fd.append('file', file)
  return post<MinioFile>('/file/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** GET /api/file/{id}/url */
export function getFileUrl(id: number): Promise<string> {
  return get<string>(`/file/${id}/url`)
}

/** GET /api/file/{id}/info */
export function getFileInfo(id: number): Promise<MinioFile> {
  return get<MinioFile>(`/file/${id}/info`)
}

/** DELETE /api/file/{id} */
export function deleteFile(id: number): Promise<void> {
  return del<void>(`/file/${id}`)
}

// ===== /api/upload/*（分片上传）=====

/** POST /api/upload/init */
export function uploadInit(data: { fileMd5: string; fileName: string; fileSize: number; totalChunks: number }): Promise<{ fileMd5: string; status: string }> {
  return post('/upload/init', data)
}

/** POST /api/upload/chunk */
export function uploadChunk(fileMd5: string, chunkIndex: number, chunkData: Blob): Promise<string> {
  const fd = new FormData()
  fd.append('fileMd5', fileMd5)
  fd.append('chunkIndex', String(chunkIndex))
  fd.append('chunkData', chunkData)
  return post('/upload/chunk', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** GET /api/upload/progress */
export function uploadProgress(fileMd5: string): Promise<{ fileMd5: string; uploadedChunks: number[]; totalChunks: number; isComplete: boolean }> {
  return get('/upload/progress', { fileMd5 } as Record<string, unknown>)
}

/** POST /api/upload/complete */
export function uploadComplete(data: { fileMd5: string; fileName: string }): Promise<{ fileId: number; minioPath: string }> {
  return post('/upload/complete', data)
}
