import { get, post, postStream } from './request'
import type { ChatRequest, ChatMessageVO } from '@/types'

// ===== /api/session =====

/** POST /api/session */
export function createSession(): Promise<{ sessionId: string }> {
  return post<{ sessionId: string }>('/session')
}

/** GET /api/session/{sessionId}/messages */
export function getMessages(sessionId: string, page: number = 1, size: number = 20): Promise<ChatMessageVO[]> {
  return get<ChatMessageVO[]>(`/session/${sessionId}/messages`, { page, size } as unknown as Record<string, unknown>)
}

/** GET /api/session/list — 获取所有会话 */
export function listSessions(): Promise<Array<{ sessionId: string; title: string; createTime: string; updateTime: string }>> {
  return get('/session/list')
}

/** GET /api/session/search — 搜索会话 */
export function searchSessions(keyword: string): Promise<Array<{ sessionId: string; title: string; createTime: string; updateTime: string }>> {
  return get('/session/search', { keyword } as Record<string, unknown>)
}

/** POST /api/session/{sessionId}/delete */
export function deleteSession(sessionId: string): Promise<void> {
  return post(`/session/${sessionId}/delete`)
}

/** POST /api/session/batch-delete */
export function batchDeleteSessions(sessionIds: string[]): Promise<void> {
  return post('/session/batch-delete', sessionIds)
}

/** GET /api/session/{sessionId}/export */
export function exportSession(sessionId: string, format: 'json' | 'markdown' = 'json'): Promise<{ format: string; content: string }> {
  return get(`/session/${sessionId}/export`, { format } as Record<string, unknown>)
}

// ===== /api/chat =====

/** POST /api/chat/stream - SSE streaming */
export function streamChat(
  data: ChatRequest,
  onDelta: (text: string) => void,
  onDone: (meta: Record<string, unknown>) => void,
  onError: (err: string) => void,
): AbortController {
  return postStream('/chat/stream', data, onDelta, onDone, onError)
}

/** POST /api/chat/regenerate - SSE regenerate from message */
export function regenerateChat(
  data: { sessionId: string; messageId: string; newContent: string },
  onDelta: (text: string) => void,
  onDone: (meta: Record<string, unknown>) => void,
  onError: (err: string) => void,
): AbortController {
  return postStream('/chat/regenerate', data, onDelta, onDone, onError)
}

// ===== /api/citation =====

/** POST /api/citation/batch-download */
export function batchDownloadCitations(
  items: Array<{ docType: string; docId: string; fileName: string }>,
): Promise<void> {
  const token = localStorage.getItem('laboa_token')
  return fetch('/api/citation/batch-download', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ items }),
  }).then(async (resp) => {
    if (!resp.ok) throw new Error('下载失败')
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'citations.zip'; a.click()
    URL.revokeObjectURL(url)
  })
}

// ===== /api/search =====

/** GET /api/search/chunks */
export function queryChunks(docType: string, docId: number): Promise<Array<Record<string, unknown>>> {
  return get<Array<Record<string, unknown>>>('/search/chunks', { docType, docId } as unknown as Record<string, unknown>)
}

/** GET /api/search/vector-search */
export function vectorSearch(
  query: string,
  topK: number = 5,
): Promise<Array<Record<string, unknown>>> {
  return get<Array<Record<string, unknown>>>('/search/vector-search', { query, topK } as unknown as Record<string, unknown>)
}
