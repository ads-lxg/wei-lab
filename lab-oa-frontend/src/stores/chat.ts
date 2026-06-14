import { defineStore } from 'pinia'
import { ref } from 'vue'
import { createSession, getMessages } from '@/api/chat'
import type { Citation } from '@/types'

export interface LocalMessage {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  ragAvailable?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref<string | null>(null)
  const messages = ref<LocalMessage[]>([])
  const isStreaming = ref(false)
  const abortController = ref<AbortController | null>(null)
  const historyLoading = ref(false)
  const sessionsDirty = ref(false) // 标记会话列表需要刷新

  /** 创建新会话（仅在用户主动新建或首次发送消息时调用） */
  async function initSession() {
    const res = await createSession()
    sessionId.value = res.sessionId
    messages.value = []
  }

  /** 加载历史会话消息，解析 citationsJson */
  async function loadHistory(sid: string) {
    historyLoading.value = true
    try {
      const msgs = await getMessages(sid, 1, 100)
      sessionId.value = sid
      messages.value = msgs.map((m: any) => {
        // 后端返回 citationsJson（字符串），需要解析
        let citations: Citation[] = []
        if (m.citationsJson) {
          try {
            citations = JSON.parse(m.citationsJson)
          } catch { /* ignore parse error */ }
        } else if (Array.isArray(m.citations)) {
          citations = m.citations
        }
        return {
          role: m.role,
          content: m.content,
          citations,
          ragAvailable: m.ragAvailable,
        }
      })
    } finally {
      historyLoading.value = false
    }
  }

  function addMessage(msg: LocalMessage) {
    messages.value.push(msg)
  }

  function stopStreaming() {
    abortController.value?.abort()
    isStreaming.value = false
  }

  function clearMessages() {
    messages.value = []
    sessionId.value = null
  }

  return {
    sessionId, messages, isStreaming, abortController, historyLoading, sessionsDirty,
    initSession, loadHistory, addMessage, stopStreaming, clearMessages,
  }
})
