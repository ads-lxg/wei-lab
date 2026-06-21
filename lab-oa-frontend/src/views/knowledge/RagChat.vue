<script setup lang="ts">
import { ref, nextTick, onMounted, onActivated, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { streamChat, listSessions as fetchSessionList, deleteSession as apiDeleteSession, searchSessions as apiSearchSessions, batchDownloadCitations } from '@/api/chat'
import { useAvatarUrl } from '@/hooks/useAvatar'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { markedHighlight } from 'marked-highlight'
import { Plus, Search, Delete, ChatLineSquare, MagicStick, Edit, User, Promotion, VideoPause, Close, Download, CopyDocument, Brush } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { escapeHtml, isDangerousUrl } from '@/utils/sanitize'
import type { LocalMessage } from '@/stores/chat'
import type { Citation } from '@/types'

// Configure marked：高亮代码 + 消毒危险链接
marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  }),
)
const renderer = new marked.Renderer()
renderer.link = ({ href, title, text }) => {
  const safeHref = isDangerousUrl(href) ? '#' : href
  return `<a href="${escapeHtml(safeHref)}" target="_blank" rel="noopener noreferrer"${title ? ` title="${escapeHtml(title)}"` : ''}>${text}</a>`
}
marked.use({ renderer })
marked.setOptions({ breaks: true, gfm: true })

const chatStore = useChatStore()
const userStore = useUserStore()
const router = useRouter()
const userAvatar = useAvatarUrl(computed(() => userStore.user?.avatar))
const input = ref('')
const chatContainer = ref<HTMLElement | null>(null)
const loading = ref(false)
const editingMsgIdx = ref(-1)
const editInput = ref('')

// ===== 对话风格选择 =====
type ChatStyle = 'default' | 'bubble' | 'anime' | 'minimal' | 'cyber' | 'academic'
const chatStyleOptions: { value: ChatStyle; label: string }[] = [
  { value: 'default', label: '默认' },
  { value: 'bubble', label: '气泡' },
  { value: 'anime', label: '动漫' },
  { value: 'minimal', label: '极简' },
  { value: 'cyber', label: '赛博朋克' },
  { value: 'academic', label: '学术' },
]
const chatStyle = ref<ChatStyle>((localStorage.getItem('chatStyle') as ChatStyle) || 'default')
function setChatStyle(style: ChatStyle) {
  chatStyle.value = style
  localStorage.setItem('chatStyle', style)
}
const chatStyleLabel = computed(() => chatStyleOptions.find(o => o.value === chatStyle.value)?.label || '默认')

// ===== 左侧历史对话 =====
interface SessionItem { sessionId: string; title: string; createTime: string; updateTime: string; messageCount?: number }
const sessions = ref<SessionItem[]>([])
const sessionsLoading = ref(false)
const searchKeyword = ref('')
const currentSessionId = computed(() => chatStore.sessionId)

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await fetchSessionList()
  } catch { /* ignore */ }
  finally { sessionsLoading.value = false }
}

async function searchSessions() {
  if (!searchKeyword.value.trim()) { await loadSessions(); return }
  sessionsLoading.value = true
  try {
    sessions.value = await apiSearchSessions(searchKeyword.value.trim())
  } catch { /* ignore */ }
  finally { sessionsLoading.value = false }
}

async function openSession(sessionId: string) {
  if (chatStore.sessionId === sessionId) return
  await chatStore.loadHistory(sessionId)
  await scrollToBottom()
}

async function newSession() {
  chatStore.clearMessages()
  // 不自动创建会话，等用户发送消息时再创建
}

async function deleteSessionItem(sessionId: string) {
  try {
    await ElMessageBox.confirm('确认删除该对话？', '删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  try {
    await apiDeleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (chatStore.sessionId === sessionId) {
      chatStore.clearMessages()
    }
    chatStore.sessionsDirty = true
    ElMessage.success('已删除')
  } catch {
    // error already shown by interceptor
  }
}

async function scrollToBottom() {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

async function handleSend() {
  const msg = input.value.trim()
  if (!msg || chatStore.isStreaming) return

  // 如果没有 sessionId，先创建一个
  if (!chatStore.sessionId) {
    await chatStore.initSession()
  }

  chatStore.addMessage({ role: 'user', content: msg })
  input.value = ''
  await scrollToBottom()

  chatStore.isStreaming = true
  loading.value = true
  chatStore.addMessage({ role: 'assistant', content: '' })
  const assistantIdx = chatStore.messages.length - 1

  const controller = streamChat(
    { sessionId: chatStore.sessionId!, message: msg },
    (delta) => {
      // 使用 Vue 响应式更新，确保视图刷新
      chatStore.messages[assistantIdx].content += delta
      scrollToBottom()
    },
    (meta) => {
      chatStore.messages[assistantIdx] = {
        ...chatStore.messages[assistantIdx],
        ragAvailable: meta.ragAvailable as boolean,
        citations: (meta.citations as any[]) || [],
      }
      chatStore.isStreaming = false
      loading.value = false
      if (!chatStore.sessionId && meta.sessionId) {
        chatStore.sessionId = meta.sessionId as string
      }
      loadSessions()
    },
    (err) => {
      chatStore.messages[assistantIdx].content += `\n\n[错误: ${err}]`
      chatStore.isStreaming = false
      loading.value = false
    },
  )
  chatStore.abortController = controller
}

function handleStop() { chatStore.stopStreaming(); loading.value = false }

/** 点击文件名：跳转到文献/文档详情页 */
function viewCitationDetail(cite: Citation) {
  if (cite.docType === 'literature' && cite.docId) {
    router.push({ name: 'DocumentDetail', params: { id: cite.docId } })
  } else if (cite.docType === 'doc' && cite.docId) {
    // 内部文档暂无独立详情页，显示片段内容
    viewCitationExcerpt(cite)
  } else {
    ElMessage.warning('无法查看该文献详情')
  }
}

/** 引用片段浮窗状态 */
const excerptVisible = ref(false)
const excerptTitle = ref('')
const excerptHtml = ref('')

/** 点击"查看"按钮：浮窗显示引用片段（无遮罩，滚轮可穿透） */
function viewCitationExcerpt(cite: Citation) {
  const excerpt = escapeHtml(cite.excerpt || '暂无片段内容')
  excerptTitle.value = `引用片段 - ${escapeHtml(cite.fileName || '未知文档')}`
  excerptHtml.value = `<div style="max-height:300px;overflow-y:auto;font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;">${excerpt}</div>`
  excerptVisible.value = true
}

/** 点击"查看"按钮（多片段）：浮窗显示同一文献的多个引用片段 */
function viewCitationExcerpts(citations: Citation[]) {
  const sections = citations.map((c, i) => {
    const excerpt = escapeHtml(c.excerpt || '暂无片段内容')
    return `<div style="margin-bottom:12px;padding-bottom:12px;${i < citations.length - 1 ? 'border-bottom:1px dashed var(--border-color)' : ''}">
      <div style="font-weight:600;color:var(--text-muted);margin-bottom:4px;">片段 ${c.referenceNumber}</div>
      <div style="font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;">${excerpt}</div>
    </div>`
  }).join('')
  excerptTitle.value = `引用片段 - ${escapeHtml(citations[0]?.fileName || '未知文档')}`
  excerptHtml.value = `<div style="max-height:400px;overflow-y:auto;">${sections}</div>`
  excerptVisible.value = true
}

/** 将同一文献的多个引用合并为一组 */
interface CitationGroup {
  key: string
  fileName: string
  refNumbers: string
  citations: Citation[]
}
function groupCitations(citations: Citation[]): CitationGroup[] {
  const map = new Map<string, Citation[]>()
  for (const cite of citations) {
    const key = `${cite.docType || ''}_${cite.docId || ''}_${cite.fileName || ''}`
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(cite)
  }
  return Array.from(map.entries()).map(([key, cites]) => ({
    key,
    fileName: cites[0].fileName,
    refNumbers: cites.map(c => c.referenceNumber).join(','),
    citations: cites,
  }))
}

/** 批量下载参考文献 */
async function handleBatchDownload(citations: Citation[]) {
  const items = citations
    .filter(c => c.docId && c.docType)
    .map(c => ({ docType: c.docType!, docId: c.docId!, fileName: c.fileName || 'unknown' }))
  if (items.length === 0) {
    ElMessage.warning('没有可下载的参考文献')
    return
  }
  try {
    await batchDownloadCitations(items)
    ElMessage.success('下载完成')
  } catch {
    ElMessage.error('下载失败')
  }
}

/** 复制消息内容（兼容非HTTPS环境的fallback方案） */
async function handleCopyMessage(msg: LocalMessage) {
  const text = msg.content || ''
  if (!text) {
    ElMessage.warning('无可复制的内容')
    return
  }
  // 优先使用 Clipboard API
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制')
      return
    } catch { /* 回退到 execCommand */ }
  }
  // Fallback: 使用 textarea + execCommand（兼容 HTTP/非安全上下文）
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '-9999px'
  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()
  try {
    const success = document.execCommand('copy')
    if (success) {
      ElMessage.success('已复制')
    } else {
      ElMessage.error('复制失败')
    }
  } catch {
    ElMessage.error('复制失败')
  } finally {
    document.body.removeChild(textarea)
  }
}

/** 编辑用户消息重新生成 */
function startEdit(idx: number) {
  const msg = chatStore.messages[idx]
  if (!msg || msg.role !== 'user') return
  editingMsgIdx.value = idx
  editInput.value = msg.content
}

function cancelEdit() { editingMsgIdx.value = -1; editInput.value = '' }

async function handleRegenerate() {
  const msg = chatStore.messages[editingMsgIdx.value]
  if (!msg) return
  msg.content = editInput.value.trim()
  chatStore.messages.splice(editingMsgIdx.value + 1)
  editingMsgIdx.value = -1
  editInput.value = ''

  if (!chatStore.sessionId) {
    await chatStore.initSession()
  }

  chatStore.isStreaming = true
  loading.value = true
  chatStore.addMessage({ role: 'assistant', content: '' })
  const assistantIdx = chatStore.messages.length - 1

  const controller = streamChat(
    { sessionId: chatStore.sessionId!, message: msg.content },
    (delta) => {
      chatStore.messages[assistantIdx].content += delta
      scrollToBottom()
    },
    (meta) => {
      chatStore.messages[assistantIdx] = {
        ...chatStore.messages[assistantIdx],
        ragAvailable: meta.ragAvailable as boolean,
        citations: (meta.citations as any[]) || [],
      }
      chatStore.isStreaming = false
      loading.value = false
    },
    (err) => {
      chatStore.messages[assistantIdx].content += `\n\n[错误: ${err}]`
      chatStore.isStreaming = false
      loading.value = false
    },
  )
  chatStore.abortController = controller
}

/** 使用 marked 渲染 Markdown，并将 [1] [2] 等引用标记渲染为上标链接 */
function renderMarkdown(text: string, citations?: Citation[]): string {
  if (!text) return ''
  // 流式容错：修复未闭合的代码块
  let safeText = text
  const codeBlockCount = (safeText.match(/```/g) || []).length
  if (codeBlockCount % 2 !== 0) {
    safeText += '\n```'
  }

  // 在 marked 解析前，先保护 [N] 引用标记，避免被 marked 误解析
  // 将 [1] [2] 等替换为占位符，marked 解析后再替换回上标链接或移除
  const citePlaceholders: string[] = []
  const maxCiteNum = citations ? citations.length : 0
  safeText = safeText.replace(/\[(\d+)\]/g, (match, numStr) => {
    const num = parseInt(numStr)
    // 如果有 citations 且该编号在 citations 中，替换为占位符
    if (citations && citations.length > 0) {
      const cite = citations.find(c => c.referenceNumber === num)
      if (cite) {
        const placeholder = `%%CITE_${num}%%`
        citePlaceholders.push(placeholder)
        return placeholder
      }
      // 编号不在 citations 中 → LLM 幻觉，标记为待移除
      const placeholder = `%%CITEINVALID_${num}%%`
      citePlaceholders.push(placeholder)
      return placeholder
    }
    // 流式过程中 citations 尚未到达，用占位符保护避免被 marked 误解析
    const placeholder = `%%CITERAW_${num}%%`
    citePlaceholders.push(placeholder)
    return placeholder
  })

  // 修复行内出现的标题标记：###标题 -> \n### 标题
  safeText = safeText.replace(/([^\n])(#{1,6})([^\s#])/g, '$1\n$2 $3')
  // 再处理行首但缺少空格的 ###标题 -> ### 标题
  safeText = safeText.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  // 修复行内出现的列表标记：-克服 -> \n- 克服
  // 注意：不处理 ** 开头的情况（那是粗体标记），只处理 - 后跟中文/字母
  safeText = safeText.replace(/([^\n*])(-[\u4e00-\u9fff\w(])/g, '$1\n$2')
  // 修复行首缺少空格的无序列表：-克服 -> - 克服
  safeText = safeText.replace(/^(-)([\u4e00-\u9fff\w(])/gm, '$1 $2')
  // 修复行首缺少空格的有序列表：1.克服 -> 1. 克服
  safeText = safeText.replace(/^(\d+\.)([^\s])/gm, '$1 $2')
  // 修复粗体标记内侧空格：** 原理 ** -> **原理**（Markdown要求**紧贴文字）
  safeText = safeText.replace(/\*\*\s+([^*]+?)\s+\*\*/g, '**$1**')
  // 修复粗体/斜体标记缺少空格：**关键词**后面紧跟中文
  safeText = safeText.replace(/(\*\*[^*]+\*\*)([\u4e00-\u9fff])/g, '$1 $2')
  // 修复粗体前面紧跟中文：中文**关键词** -> 中文 **关键词**
  safeText = safeText.replace(/([\u4e00-\u9fff])(\*\*[^*]+\*\*)/g, '$1 $2')

  let html = ''
  try { html = marked.parse(safeText) as string } catch { html = safeText.replace(/\n/g, '<br>') }

  // 将占位符替换为可点击的上标链接或移除无效引用
  if (citePlaceholders.length > 0) {
    for (const placeholder of citePlaceholders) {
      // 有 citation 定义的引用 -> 上标链接
      const citeMatch = placeholder.match(/%%CITE_(\d+)%%/)
      if (citeMatch) {
        const num = parseInt(citeMatch[1])
        const cite = citations?.find(c => c.referenceNumber === num)
        if (cite) {
          html = html.replace(placeholder, `<sup><a href="javascript:void(0)" class="cite-ref" data-ref="${num}" title="${cite.fileName}">[${num}]</a></sup>`)
        }
        continue
      }
      // 无效引用（LLM 幻觉）→ 直接移除
      const invalidMatch = placeholder.match(/%%CITEINVALID_(\d+)%%/)
      if (invalidMatch) {
        html = html.replace(placeholder, '')
        continue
      }
      // 流式过程中暂无 citation 定义的引用 -> 保留原始 [N] 显示
      const rawMatch = placeholder.match(/%%CITERAW_(\d+)%%/)
      if (rawMatch) {
        const num = parseInt(rawMatch[1])
        html = html.replace(placeholder, `<sup class="cite-pending">[${num}]</sup>`)
      }
    }
    // 兜底：处理可能被 marked 拆分的占位符
    html = html.replace(/%%CITE_(\d+)%%/g, (match, numStr) => {
      const num = parseInt(numStr)
      const cite = citations?.find(c => c.referenceNumber === num)
      if (cite) {
        return `<sup><a href="javascript:void(0)" class="cite-ref" data-ref="${num}" title="${cite.fileName}">[${num}]</a></sup>`
      }
      return `[${num}]`
    })
    html = html.replace(/%%CITEINVALID_\d+%%/g, '')
    html = html.replace(/%%CITERAW_(\d+)%%/g, (match, numStr) => {
      return `<sup class="cite-pending">[${numStr}]</sup>`
    })
  }

  return html
}

/** 处理引用标记点击事件 */
function handleCiteClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const citeRef = target.classList.contains('cite-ref') ? target : target.closest('.cite-ref') as HTMLElement | null
  const citePending = !citeRef && (target.classList.contains('cite-pending') ? target : target.closest('.cite-pending') as HTMLElement | null)

  if (citeRef) {
    const refNum = parseInt(citeRef.getAttribute('data-ref') || '0')
    if (refNum > 0) {
      // 限定在当前消息的参考文献区域内查找（避免跳转到其他消息的引用）
      const msgBubble = citeRef.closest('[data-msg-idx]') || citeRef.closest('.markdown-body')?.parentElement
      // 在分组后的引用列表中查找包含该编号的项
      const citeEls = msgBubble?.querySelectorAll('[data-cite-nums]') || []
      let citeEl: Element | null = null
      for (const el of citeEls) {
        const nums = (el.getAttribute('data-cite-nums') || '').split(',').map(Number)
        if (nums.includes(refNum)) { citeEl = el; break }
      }
      if (!citeEl) {
        citeEl = chatContainer.value?.querySelector(`[data-cite-nums]`) || null
      }
      if (citeEl) {
        citeEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
        citeEl.classList.add('cite-highlight')
        setTimeout(() => citeEl!.classList.remove('cite-highlight'), 2000)
      }
    }
  } else if (citePending) {
    ElMessage.info('引用信息加载中，请稍候...')
  }
}

onMounted(async () => {
  await loadSessions()
  // 不自动创建会话！等用户点击"新建对话"或发送消息时才创建
})

onActivated(async () => {
  // 从对话历史页删除会话后，刷新侧边栏列表
  if (chatStore.sessionsDirty) {
    await loadSessions()
    chatStore.sessionsDirty = false
  }
})
</script>

<template>
  <div class="flex gap-0 h-[calc(100vh-116px)] -m-6">
    <!-- Left: Session History Sidebar -->
    <div class="w-60 shrink-0 flex flex-col overflow-hidden" style="background: var(--bg-sidebar); border-right: 1px solid var(--border-color)">
      <div class="flex items-center justify-between p-3 border-b shrink-0" style="border-color: var(--border-color)">
        <span class="text-xs font-semibold uppercase" style="color: var(--text-muted)">历史对话</span>
        <el-button size="small" :icon="Plus" text @click="newSession" title="新建对话" />
      </div>
      <!-- Search -->
      <div class="px-2 py-2 shrink-0">
        <el-input v-model="searchKeyword" placeholder="搜索对话..." size="small" clearable :prefix-icon="Search" @keyup.enter="searchSessions" @clear="loadSessions" />
      </div>
      <div class="flex-1 overflow-auto px-2 pb-2 space-y-0.5">
        <div v-if="sessions.length === 0 && !sessionsLoading" class="text-center text-xs py-8" style="color: var(--text-muted)">
          暂无历史对话
        </div>
        <div v-loading="sessionsLoading" class="space-y-0.5">
          <div
            v-for="session in sessions" :key="session.sessionId"
            class="group flex items-center gap-1 px-2 py-2 rounded-lg text-xs cursor-pointer transition-colors"
            :class="session.sessionId === currentSessionId ? 'bg-primary-50 dark:bg-primary-500/20 text-primary-600 dark:text-primary-300' : 'hover:bg-slate-50 dark:hover:bg-zinc-800/50'"
            :style="session.sessionId === currentSessionId ? {} : { color: 'var(--text-secondary)' }"
          >
            <div class="flex-1 min-w-0" @click="openSession(session.sessionId)">
              <p class="truncate font-medium">{{ session.title || '无标题对话' }}</p>
              <p class="text-[10px] opacity-60 mt-0.5">{{ session.updateTime?.substring(0, 10) || session.createTime?.substring(0, 10) }}</p>
            </div>
            <el-button
              size="small" text :icon="Delete"
              class="!p-1 opacity-0 group-hover:opacity-100 transition-opacity"
              style="color: var(--text-muted)"
              @click.stop="deleteSessionItem(session.sessionId)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Right: Chat -->
    <div class="flex-1 flex flex-col min-w-0">
      <div ref="chatContainer" class="flex-1 overflow-auto p-4 space-y-4">
        <div v-if="chatStore.messages.length === 0" class="flex items-center justify-center h-full">
          <div class="text-center">
            <div class="w-16 h-16 rounded-2xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 mx-auto mb-4">
              <el-icon :size="28"><ChatLineSquare /></el-icon>
            </div>
            <h3 class="text-lg font-semibold" style="color: var(--text-primary)">RAG 智能问答</h3>
            <p class="text-sm mt-1" style="color: var(--text-muted)">基于文献库的知识问答助手</p>
          </div>
        </div>

        <div
          v-for="(msg, idx) in chatStore.messages" :key="idx"
          class="flex gap-3"
          :class="msg.role === 'user' ? 'justify-end' : ''"
        >
          <!-- Assistant avatar -->
          <div v-if="msg.role === 'assistant'" class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
            <el-icon :size="16"><MagicStick /></el-icon>
          </div>

          <!-- Bubble + action buttons column -->
          <div class="max-w-[75%] flex flex-col group" :class="msg.role === 'user' ? 'items-end' : 'items-start'">
            <div
              class="px-4 py-3 text-sm leading-relaxed msg-bubble"
              :class="[
                msg.role === 'user' ? 'msg-user' : 'msg-assistant',
                `style-${chatStyle}`,
              ]"
            >
              <!-- Edit mode -->
              <template v-if="editingMsgIdx === idx">
                <el-input v-model="editInput" type="textarea" :rows="2" size="small" class="mb-2" />
                <div class="flex gap-1">
                  <el-button size="small" type="primary" @click="handleRegenerate" :disabled="!editInput.trim() || chatStore.isStreaming">重新生成</el-button>
                  <el-button size="small" @click="cancelEdit">取消</el-button>
                </div>
              </template>

              <!-- Normal display -->
              <template v-else>
                <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(msg.content, msg.citations)" @click="handleCiteClick" />
                <div v-else class="bubble-text select-text">{{ msg.content }}</div>
              </template>

              <!-- Citations (clickable, grouped by same document) -->
              <div v-if="msg.role === 'assistant' && msg.citations && msg.citations.length > 0" class="mt-3 pt-3" style="border-top: 1px solid var(--border-color)" :data-msg-idx="idx">
                <div class="flex items-center justify-between mb-2">
                  <p class="text-xs font-medium" style="color: var(--text-muted)">参考文献 ({{ groupCitations(msg.citations).length }})</p>
                  <el-button size="small" text type="primary" :icon="Download" class="!text-[10px] !p-0" @click="handleBatchDownload(msg.citations)">批量下载</el-button>
                </div>
                <div
                  v-for="group in groupCitations(msg.citations)" :key="group.key"
                  :data-cite-nums="group.citations.map(c => c.referenceNumber).join(',')"
                  class="cite-item flex items-center gap-2 text-xs py-1.5 px-2 rounded transition-colors group"
                >
                  <span
                    class="flex-1 truncate cursor-pointer hover:text-primary-500 transition-colors"
                    style="color: var(--text-primary)"
                    :title="group.fileName"
                    @click="viewCitationDetail(group.citations[0])"
                  >{{ group.fileName }}<sup class="text-primary-500 ml-0.5">{{ group.refNumbers }}</sup></span>
                  <el-button
                    size="small" text type="primary"
                    class="!p-0 !text-[10px] shrink-0"
                    @click="group.citations.length === 1 ? viewCitationExcerpt(group.citations[0]) : viewCitationExcerpts(group.citations)"
                  >查看</el-button>
                </div>
              </div>
            </div>

            <!-- Action buttons row (copy + edit, same column below bubble) -->
            <div class="flex items-center gap-1 mt-1 transition-opacity" :class="msg.role === 'user' ? 'flex-row-reverse' : ''">
              <el-button size="small" text :icon="CopyDocument" class="!p-1 !text-xs" style="color: var(--text-muted)" @click.stop="handleCopyMessage(msg)" title="复制内容" />
              <el-button v-if="msg.role === 'user' && editingMsgIdx !== idx" size="small" text :icon="Edit" class="!p-1 !text-xs" style="color: var(--text-muted)" @click="startEdit(idx)" title="编辑消息" />
            </div>
          </div>

          <!-- User avatar -->
          <div v-if="msg.role === 'user'" class="w-8 h-8 rounded-lg shrink-0 flex items-center justify-center overflow-hidden" :style="userAvatar ? {} : { background: 'var(--bg-hover)', color: 'var(--text-muted)' }">
            <img v-if="userAvatar" :src="userAvatar" alt="avatar" class="w-full h-full object-cover" />
            <el-icon v-else :size="16"><User /></el-icon>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex gap-3">
          <div class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
            <el-icon :size="16"><MagicStick /></el-icon>
          </div>
          <div class="rounded-2xl rounded-bl-md px-4 py-3" style="background: var(--bg-hover)">
            <span class="flex gap-1">
              <span class="w-2 h-2 bg-slate-300 rounded-full animate-bounce" />
              <span class="w-2 h-2 bg-slate-300 rounded-full animate-bounce" style="animation-delay:0.1s" />
              <span class="w-2 h-2 bg-slate-300 rounded-full animate-bounce" style="animation-delay:0.2s" />
            </span>
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="p-4 shrink-0" style="border-top: 1px solid var(--border-color)">
        <div class="flex items-end gap-2 max-w-3xl mx-auto">
          <el-input
            v-model="input"
            type="textarea"
            :rows="1"
            placeholder="输入您的问题..."
            class="flex-1"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
            :disabled="chatStore.isStreaming"
          />
          <el-button v-if="!chatStore.isStreaming" type="primary" :icon="Promotion" :disabled="!input.trim()" @click="handleSend" />
          <el-button v-else type="danger" :icon="VideoPause" @click="handleStop">停止</el-button>
          <!-- 风格选择器 -->
          <el-dropdown trigger="click" @command="setChatStyle">
            <el-button size="default" :icon="Brush" title="对话风格: {{ chatStyleLabel }}">
              <span class="ml-1 text-xs">{{ chatStyleLabel }}</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="opt in chatStyleOptions" :key="opt.value"
                  :command="opt.value"
                  :class="{ 'is-active': chatStyle === opt.value }"
                >{{ opt.label }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <!-- 引用片段浮窗（无遮罩，滚轮可穿透到聊天区域） -->
    <Teleport to="body">
      <div
        v-if="excerptVisible"
        class="excerpt-overlay"
        @click.self="excerptVisible = false"
      >
        <div class="excerpt-panel" @click.stop>
          <div class="excerpt-header">
            <span class="excerpt-title">{{ excerptTitle }}</span>
            <el-button size="small" text @click="excerptVisible = false" class="!p-1">
              <el-icon :size="16"><Close /></el-icon>
            </el-button>
          </div>
          <div class="excerpt-body" v-html="excerptHtml"></div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ——— 选中文字高亮 ——— */
.markdown-body :deep(*)::selection,
.bubble-text::selection,
.select-text::selection {
  background: #3b82f6 !important; color: #fff !important;
}
.markdown-body :deep(*)::-moz-selection,
.bubble-text::-moz-selection,
.select-text::-moz-selection {
  background: #3b82f6 !important; color: #fff !important;
}
/* 用户气泡（蓝色背景）中选中文字用亮色 */
.msg-user .bubble-text::selection,
.msg-user .select-text::selection {
  background: #fbbf24 !important; color: #1e1b4b !important;
}
.msg-user .bubble-text::-moz-selection,
.msg-user .select-text::-moz-selection {
  background: #fbbf24 !important; color: #1e1b4b !important;
}

/* ============================================================
   默认风格
   ============================================================ */
.style-default.msg-assistant {
  background: var(--bg-card);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: 16px 4px 16px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.style-default.msg-user {
  background: #3b82f6;
  color: #fff;
  border-radius: 4px 16px 16px 16px;
}

/* ============================================================
   气泡风格 — 圆润、色彩鲜明、尾巴
   ============================================================ */
.style-bubble.msg-assistant {
  background: #f0f4ff;
  color: var(--text-primary);
  border: none;
  border-radius: 20px 4px 20px 20px;
  box-shadow: 0 2px 8px rgba(59,130,246,0.10);
}
.style-bubble.msg-user {
  background: linear-gradient(135deg, #6366f1, #3b82f6);
  color: #fff;
  border-radius: 4px 20px 20px 20px;
  box-shadow: 0 2px 8px rgba(99,102,241,0.25);
}
/* 气泡风格：用户消息选中文字更亮 */
.style-bubble .bubble-text::selection {
  background: #fbbf24 !important; color: #1e1b4b !important;
}

/* ============================================================
   动漫风格 — 圆角边框、柔和阴影、活泼配色
   ============================================================ */
.style-anime.msg-assistant {
  background: #fff7ed;
  color: var(--text-primary);
  border: 2px solid #fdba74;
  border-radius: 20px 6px 20px 20px;
  box-shadow: 0 3px 12px rgba(251,146,60,0.15);
}
.style-anime.msg-user {
  background: linear-gradient(135deg, #f472b6, #ec4899);
  color: #fff;
  border: 2px solid #f9a8d4;
  border-radius: 6px 20px 20px 20px;
  box-shadow: 0 3px 12px rgba(244,114,182,0.25);
}
/* 动漫风格：用户消息选中文字 */
.style-anime .bubble-text::selection {
  background: #fbbf24 !important; color: #831843 !important;
}
/* 动漫风格：助手消息代码块 */
.style-anime .markdown-body :deep(pre) {
  border: 1px dashed #fdba74;
}

/* ============================================================
   极简风格 — 无边框、无阴影、纯净留白
   ============================================================ */
.style-minimal.msg-assistant {
  background: transparent;
  color: var(--text-primary);
  border: none;
  border-radius: 0;
  box-shadow: none;
  border-left: 3px solid #94a3b8;
  padding-left: 16px;
}
.style-minimal.msg-user {
  background: transparent;
  color: var(--text-primary);
  border: none;
  border-radius: 0;
  box-shadow: none;
  border-left: 3px solid #3b82f6;
  padding-left: 16px;
}
.style-minimal .bubble-text {
  color: var(--text-primary);
}

/* ============================================================
   赛博朋克风格 — 霓虹光效、暗色背景、科技感
   ============================================================ */
.style-cyber.msg-assistant {
  background: linear-gradient(135deg, #0f172a, #1e1b4b);
  color: #a5f3fc;
  border: 1px solid #06b6d4;
  border-radius: 4px 16px 16px 16px;
  box-shadow: 0 0 12px rgba(6,182,212,0.3), inset 0 0 8px rgba(6,182,212,0.05);
}
.style-cyber.msg-user {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
  color: #f0abfc;
  border: 1px solid #c084fc;
  border-radius: 16px 4px 16px 16px;
  box-shadow: 0 0 12px rgba(168,85,247,0.4);
}
.style-cyber .bubble-text::selection {
  background: #06b6d4 !important; color: #0f172a !important;
}
.style-cyber .markdown-body :deep(code) {
  background: rgba(6,182,212,0.15); color: #67e8f9;
}
.style-cyber .markdown-body :deep(pre) {
  background: #020617; border: 1px solid #06b6d4; color: #a5f3fc;
  box-shadow: 0 0 8px rgba(6,182,212,0.2);
}
.style-cyber .markdown-body :deep(a) {
  color: #22d3ee;
}
/* 赛博朋克风格：参考文献文件名用高对比度洋红/白色，避免与青色背景撞色 */
.style-cyber .cite-item span {
  color: #f0abfc !important;
  text-shadow: 0 0 4px rgba(240, 171, 252, 0.4);
}
.style-cyber .cite-item:hover {
  background: rgba(6, 182, 212, 0.12);
}
.style-cyber .cite-item sup {
  color: #ffffff !important;
}

/* ============================================================
   学术风格 — 正式、衬线字体、论文质感
   ============================================================ */
.style-academic.msg-assistant {
  background: #fefce8;
  color: #422006;
  border: 1px solid #ca8a04;
  border-radius: 2px 2px 2px 2px;
  box-shadow: none;
  font-family: 'Georgia', 'Times New Roman', serif;
}
.style-academic.msg-user {
  background: #1c1917;
  color: #fef3c7;
  border: 1px solid #78716c;
  border-radius: 2px 2px 2px 2px;
  box-shadow: none;
  font-family: 'Georgia', 'Times New Roman', serif;
}
.style-academic .bubble-text {
  font-family: 'Georgia', 'Times New Roman', serif;
}
.style-academic .markdown-body :deep(p) {
  text-indent: 2em; line-height: 1.8;
}
.style-academic .markdown-body :deep(code) {
  background: rgba(202,138,4,0.1); color: #854d0e;
}
.style-academic .markdown-body :deep(pre) {
  background: #292524; color: #fef3c7; border: 1px solid #78716c;
}
.style-academic .markdown-body :deep(blockquote) {
  border-left: 3px solid #ca8a04; background: rgba(202,138,4,0.05);
}

/* ——— Markdown 渲染 ——— */
.markdown-body :deep(h1) { font-size: 1.4em; font-weight: 700; margin: 0.6em 0 0.3em; }
.markdown-body :deep(h2) { font-size: 1.2em; font-weight: 600; margin: 0.5em 0 0.3em; }
.markdown-body :deep(h3) { font-size: 1.1em; font-weight: 600; margin: 0.4em 0 0.2em; }
.markdown-body :deep(p) { margin: 0.4em 0; line-height: 1.7; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 1.5em; margin: 0.4em 0; }
.markdown-body :deep(li) { margin: 0.2em 0; }
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.06); padding: 0.15em 0.4em; border-radius: 4px;
  font-size: 0.9em; font-family: 'SF Mono', 'Fira Code', monospace;
}
.markdown-body :deep(pre) {
  margin: 0.6em 0; padding: 0.8em 1em; border-radius: 8px; overflow-x: auto;
  background: #1e1e2e; color: #cdd6f4; font-size: 0.85em;
}
.markdown-body :deep(pre code) {
  background: none; padding: 0; border-radius: 0; font-size: inherit;
}
.markdown-body :deep(blockquote) {
  margin: 0.4em 0; padding: 0.3em 0.8em; border-left: 3px solid var(--border-color);
  color: var(--text-secondary); background: var(--bg-hover); border-radius: 0 4px 4px 0;
}
.markdown-body :deep(a) { color: var(--el-color-primary); text-decoration: underline; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; font-size: 0.9em; }
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--border-color); padding: 0.4em 0.6em; text-align: left;
}
.markdown-body :deep(th) { background: var(--bg-hover); font-weight: 600; }
.markdown-body :deep(hr) { margin: 0.8em 0; border: none; border-top: 1px solid var(--border-color); }
.markdown-body :deep(img) { max-width: 100%; border-radius: 6px; margin: 0.4em 0; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(em) { font-style: italic; }
.markdown-body :deep(.cite-ref) {
  color: var(--el-color-primary); text-decoration: none; cursor: pointer;
  font-weight: 600; font-size: 0.8em; padding: 0 1px;
  border-radius: 2px; transition: background 0.2s;
}
.markdown-body :deep(.cite-ref:hover) {
  background: rgba(var(--el-color-primary-rgb), 0.15);
}
.markdown-body :deep(.cite-pending) {
  color: var(--el-color-primary); font-size: 0.8em; opacity: 0.7;
}
.cite-highlight {
  background: rgba(var(--el-color-primary-rgb), 0.15) !important;
  transition: background 0.3s;
}

/* 引用片段浮窗 */
.excerpt-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  pointer-events: none;
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 60px 20px 20px 20px;
}
.excerpt-panel {
  pointer-events: auto;
  width: 420px;
  max-width: 90vw;
  max-height: 70vh;
  background: var(--el-bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.excerpt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  font-size: 14px;
  font-weight: 600;
}
.excerpt-title {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.excerpt-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}
</style>
