<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDocPage, createOnlineDoc, createDoc, deleteDoc, getDocById, searchDocs } from '@/api/doc'
import type { MdDocument } from '@/api/doc'
import type { PageResult } from '@/types'
import { marked } from 'marked'
import { Upload, Plus, Search, Document, UploadFilled, Download } from '@element-plus/icons-vue'

marked.setOptions({ breaks: true, gfm: true })

const loading = ref(false)
const docs = ref<MdDocument[]>([])
const total = ref(0)
const pageVal = ref(1)
const size = ref(10)
const keyword = ref('')
const searchMode = ref<'meta' | 'fulltext'>('meta')

// Edit dialog
const editVisible = ref(false)
const editTitle = ref('')
const editContent = ref('')
const editFileType = ref<'md' | 'txt'>('md')
const saving = ref(false)

// Upload dialog
const uploadVisible = ref(false)
const uploadTitle = ref('')
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

// View dialog
const viewVisible = ref(false)
const viewTitle = ref('')
const viewContent = ref('')
const viewFileType = ref('md')
const viewUrl = ref('')

async function fetchData() {
  loading.value = true
  try {
    if (searchMode.value === 'fulltext' && keyword.value.trim()) {
      const result = await searchDocs(keyword.value.trim(), pageVal.value, size.value)
      // searchDocs returns SearchResult with hits array
      const hits = result?.hits || []
      docs.value = hits.map((h: any) => ({
        id: h.id || h.docId,
        title: h.fileName || h.title || '未知',
        fileId: h.fileId,
        fileType: h.fileType || 'md',
        authorId: h.authorId || 0,
        status: 1,
        createTime: h.createTime || '',
        updateTime: h.updateTime || '',
      }))
      total.value = result?.total || docs.value.length
    } else {
      const result: PageResult<MdDocument> = await getDocPage({ page: pageVal.value, size: size.value, keyword: keyword.value || undefined })
      docs.value = result.records
      total.value = result.total
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openCreate() {
  editTitle.value = ''
  editContent.value = ''
  editFileType.value = 'md'
  editVisible.value = true
}

async function handleSave() {
  if (!editTitle.value.trim()) { ElMessage.warning('请输入标题'); return }
  if (!editContent.value.trim()) { ElMessage.warning('请输入内容'); return }
  saving.value = true
  try {
    await createOnlineDoc({
      title: editTitle.value,
      content: editContent.value,
      fileType: editFileType.value,
    })
    ElMessage.success('创建成功')
    editVisible.value = false
    fetchData()
  } finally { saving.value = false }
}

async function handleDelete(doc: MdDocument) {
  try {
    await ElMessageBox.confirm(`确认删除文档 "${doc.title}"？`, '删除', { type: 'warning' })
    await deleteDoc(doc.id)
    ElMessage.success('已删除')
    fetchData()
  } catch { /* cancelled */ }
}

// ===== 查看文档 =====
async function handleView(doc: MdDocument) {
  try {
    const res = await getDocById(doc.id)
    viewTitle.value = res.doc?.title || doc.title
    viewContent.value = res.content || ''
    viewFileType.value = res.doc?.fileType || doc.fileType || 'md'
    viewUrl.value = res.url || ''
    viewVisible.value = true
  } catch {
    ElMessage.error('加载文档失败')
  }
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  try { return marked.parse(text) as string } catch { return text }
}

/** 下载文档（查看弹窗内） */
function handleDownload() {
  if (!viewContent.value && !viewUrl.value) {
    ElMessage.warning('无下载内容')
    return
  }
  // 优先用 content 创建 Blob 下载，避免 presigned URL 在浏览器中乱码
  if (viewContent.value) {
    const ext = viewFileType.value === 'txt' ? 'txt' : 'md'
    const mimeType = viewFileType.value === 'txt' ? 'text/plain' : 'text/markdown'
    const blob = new Blob([viewContent.value], { type: `${mimeType};charset=utf-8` })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${viewTitle.value || 'document'}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
  } else {
    window.open(viewUrl.value, '_blank')
  }
}

/** 直接下载文档（列表操作列） */
async function handleDownloadDoc(doc: MdDocument) {
  try {
    const res = await getDocById(doc.id)
    if (res?.content) {
      const ext = res.doc?.fileType === 'txt' ? 'txt' : 'md'
      const mimeType = ext === 'txt' ? 'text/plain' : 'text/markdown'
      const blob = new Blob([res.content], { type: `${mimeType};charset=utf-8` })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${res.doc?.title || doc.title || 'document'}.${ext}`
      a.click()
      URL.revokeObjectURL(url)
    } else if (res?.url) {
      window.open(res.url, '_blank')
    } else {
      ElMessage.warning('无下载内容')
    }
  } catch {
    ElMessage.error('下载失败')
  }
}

// ===== 上传文件 =====
function openUpload() {
  uploadTitle.value = ''
  uploadFile.value = null
  uploadVisible.value = true
}

function handleUploadFile(f: any) {
  uploadFile.value = f?.raw || f
}

async function handleUpload() {
  if (!uploadTitle.value.trim()) { ElMessage.warning('请输入标题'); return }
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    await createDoc(uploadTitle.value.trim(), uploadFile.value)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    fetchData()
  } finally { uploading.value = false }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">知识储备</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">在线编写 Markdown / TXT 文档，自动纳入 RAG 知识库</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button @click="openUpload">
          <el-icon :size="14"><Upload /></el-icon> 上传文档
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon> 新建文档
        </el-button>
      </div>
    </div>

    <div class="card">
      <div class="flex items-center gap-2 p-3 border-b" style="border-color: var(--border-color)">
        <el-input v-model="keyword" placeholder="搜索文档..." size="small" class="max-w-[240px]" :prefix-icon="Search" clearable @clear="fetchData" @keyup.enter="fetchData" />
        <el-button size="small" @click="fetchData">搜索</el-button>
        <el-radio-group v-model="searchMode" size="small" @change="fetchData">
          <el-radio-button value="meta">元数据</el-radio-button>
          <el-radio-button value="fulltext">全文</el-radio-button>
        </el-radio-group>
        <div class="flex-1" />
        <el-tag size="small" effect="plain">共 {{ total }} 篇</el-tag>
      </div>

      <el-table :data="docs" v-loading="loading" size="small" stripe row-key="id" style="width: 100%">
        <el-table-column label="标题" min-width="250">
          <template #default="{ row }">
            <div class="flex items-center gap-2.5 cursor-pointer" @click="handleView(row)">
              <div class="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-500/20 flex items-center justify-center text-emerald-500 shrink-0">
                <el-icon :size="14"><Document /></el-icon>
              </div>
              <span class="text-sm font-medium hover:text-primary-500 transition-colors" style="color: var(--text-primary)">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <span class="badge" :class="row.fileType === 'md' ? 'badge-primary' : 'badge-warning'">{{ row.fileType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="140" align="center">
          <template #default="{ row }">{{ row.createTime?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleView(row)">查看</el-button>
            <el-button size="small" type="primary" link @click="handleDownloadDoc(row)">下载</el-button>
            <el-popconfirm title="确认删除此文档？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-between items-center px-4 py-3 border-t" style="border-color: var(--border-color)">
        <span class="text-xs" style="color: var(--text-muted)">第 {{ pageVal }} 页 · 共 {{ total }} 条</span>
        <el-pagination v-model:current-page="pageVal" :page-size="size" :total="total" background small layout="prev, pager, next" @current-change="fetchData" />
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="editVisible" title="新建文档" width="700px" top="5vh">
      <div class="space-y-4">
        <div class="flex items-center gap-3">
          <el-input v-model="editTitle" placeholder="文档标题" size="large" class="flex-1" />
          <el-select v-model="editFileType" size="large" style="width: 100px">
            <el-option label="Markdown" value="md" />
            <el-option label="纯文本" value="txt" />
          </el-select>
        </div>
        <div>
          <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">
            内容（{{ editFileType === 'md' ? '支持 Markdown 语法' : '纯文本' }}）
          </label>
          <el-input v-model="editContent" type="textarea" :rows="15" placeholder="开始编写..." class="font-mono text-sm" />
        </div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!editTitle.trim() || !editContent.trim()" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="500px" top="20vh">
      <div class="space-y-4">
        <el-input v-model="uploadTitle" placeholder="文档标题" size="large" />
        <el-upload :auto-upload="false" :limit="1" :on-change="handleUploadFile" drag class="w-full" accept=".md,.txt,.pdf,.docx">
          <el-icon :size="36" class="text-slate-300 mb-2"><UploadFilled /></el-icon>
          <div class="text-sm" style="color: var(--text-muted)">拖拽或点击选择文件</div>
          <template #tip><div class="text-xs mt-1" style="color: var(--text-muted)">支持 MD、TXT、PDF、DOCX 格式</div></template>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadTitle.trim() || !uploadFile" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- View Dialog -->
    <el-dialog v-model="viewVisible" :title="viewTitle" width="800px" top="5vh">
      <div class="mb-3 flex items-center gap-2">
        <el-tag size="small" :type="viewFileType === 'md' ? 'primary' : 'warning'">{{ viewFileType }}</el-tag>
        <el-button size="small" type="primary" :icon="Download" @click="handleDownload">下载文件</el-button>
      </div>
      <div v-if="viewFileType === 'md'" class="markdown-viewer prose max-w-none p-4 rounded-lg" style="background: var(--bg-hover)" v-html="renderMarkdown(viewContent)" />
      <pre v-else class="p-4 rounded-lg text-sm whitespace-pre-wrap" style="background: var(--bg-hover); color: var(--text-primary)">{{ viewContent }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.markdown-viewer :deep(h1) { font-size: 1.5em; font-weight: 700; margin: 0.6em 0 0.3em; }
.markdown-viewer :deep(h2) { font-size: 1.3em; font-weight: 600; margin: 0.5em 0 0.3em; }
.markdown-viewer :deep(h3) { font-size: 1.1em; font-weight: 600; margin: 0.4em 0 0.2em; }
.markdown-viewer :deep(p) { margin: 0.5em 0; line-height: 1.8; }
.markdown-viewer :deep(ul), .markdown-viewer :deep(ol) { padding-left: 1.5em; margin: 0.5em 0; }
.markdown-viewer :deep(li) { margin: 0.2em 0; }
.markdown-viewer :deep(code) { background: rgba(0,0,0,0.06); padding: 0.15em 0.4em; border-radius: 4px; font-size: 0.9em; }
.markdown-viewer :deep(pre) { margin: 0.6em 0; padding: 0.8em 1em; border-radius: 8px; overflow-x: auto; background: #1e1e2e; color: #cdd6f4; font-size: 0.85em; }
.markdown-viewer :deep(pre code) { background: none; padding: 0; border-radius: 0; }
.markdown-viewer :deep(blockquote) { margin: 0.5em 0; padding: 0.3em 0.8em; border-left: 3px solid var(--border-color); color: var(--text-secondary); }
.markdown-viewer :deep(a) { color: var(--el-color-primary); text-decoration: underline; }
.markdown-viewer :deep(table) { border-collapse: collapse; width: 100%; margin: 0.6em 0; font-size: 0.9em; }
.markdown-viewer :deep(th), .markdown-viewer :deep(td) { border: 1px solid var(--border-color); padding: 0.4em 0.6em; text-align: left; }
.markdown-viewer :deep(th) { background: var(--bg-hover); font-weight: 600; }
</style>
