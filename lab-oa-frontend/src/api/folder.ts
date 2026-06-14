import { get, post, put, del } from './request'
import type { FolderTreeVO, FolderCreateDTO, FolderUpdateDTO, FolderMoveDTO } from '@/types'

/** GET /api/rag/folder/tree */
export function getFolderTree(): Promise<FolderTreeVO[]> {
  return get<FolderTreeVO[]>('/rag/folder/tree')
}

/** POST /api/rag/folder/create */
export function createFolder(data: FolderCreateDTO): Promise<number> {
  return post<number>('/rag/folder/create', data)
}

/** PUT /api/rag/folder/update */
export function updateFolder(data: FolderUpdateDTO): Promise<void> {
  return put<void>('/rag/folder/update', data)
}

/** DELETE /api/rag/folder/{id} */
export function deleteFolder(id: number): Promise<void> {
  return del<void>(`/rag/folder/${id}`)
}

/** PUT /api/rag/folder/move */
export function moveFolder(data: FolderMoveDTO): Promise<void> {
  return put<void>('/rag/folder/move', data)
}
