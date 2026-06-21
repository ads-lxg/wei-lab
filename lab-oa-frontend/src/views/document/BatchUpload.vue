<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFolderStore } from '@/stores/folder'
import { batchUploadDocuments, batchParseDoi, batchDeleteDocuments } from '@/api/document'
import { Download, UploadFilled, Document } from '@element-plus/icons-vue'
import type { BatchUploadResultVO } from '@/types'

const router = useRouter()
const route = useRoute()
const folderStore = useFolderStore()

const folderId = ref<number | string | null>(null)
const excelFile = ref<File | null>(null)
const docFiles = ref<File[]>([])
const uploading = ref(false)
const doiParsing = ref(false)
const result = ref<BatchUploadResultVO | null>(null)
const deletingFailed = ref(false)

function findFolderName(id: number | string | null | undefined, tree: any[]): string {
  if (id === null || id === undefined) return ''
  for (const node of tree) {
    // 同时处理 number 和 string 类型
    if (String(node.id) === String(id)) return node.folderName
    if (node.children?.length) {
      const found = findFolderName(id, node.children)
      if (found) return found
    }
  }
  return ''
}

const currentFolderName = computed(() => {
  if (!folderId.value) return ''
  // 先从选中节点直接获取
  const flat = (nodes: any[]): any[] => {
    const result: any[] = []
    for (const n of nodes) {
      result.push(n)
      if (n.children) result.push(...flat(n.children))
    }
    return result
  }
  const match = flat(folderStore.tree).find(n => String(n.id) === String(folderId.value))
  return match ? match.folderName : ''
})

function handleExcelChange(f: any) {
  excelFile.value = f?.raw || f
}
function handleFilesChange(f: any, list: any[]) {
  docFiles.value = list.map((item: any) => item.raw || item)
}

async function handleDoiImport(f: any) {
  const file = f?.raw || f
  if (!file) return
  doiParsing.value = true
  try {
    await batchParseDoi(file)
    ElMessage.success('DOI解析完成，已下载填充后的Excel，可直接用于批量上传')
  } catch {
    ElMessage.error('DOI解析失败，请检查Excel格式')
  } finally {
    doiParsing.value = false
  }
}

async function handleUpload() {
  if (!excelFile.value || docFiles.value.length === 0 || !folderId.value) {
    ElMessage.warning('请选择目录、Excel元数据和文献文件')
    return
  }
  uploading.value = true
  result.value = null
  try {
    const fd = new FormData()
    fd.append('folderId', String(folderId.value))
    fd.append('excelFile', excelFile.value)
    for (const f of docFiles.value) {
      fd.append('files', f)
    }
    const res = await batchUploadDocuments(fd)
    result.value = res
    if (res.failCount > 0 && res.successCount === 0) {
      ElMessage.error(`上传失败：全部 ${res.failCount} 个文件未成功`)
    } else if (res.failCount > 0) {
      ElMessage.warning(`部分成功：${res.successCount} 个成功，${res.failCount} 个失败`)
    } else {
      ElMessage.success(`上传完成：全部 ${res.successCount} 个成功`)
    }
  } finally {
    uploading.value = false
  }
}

/** 批量删除上传成功的文献（移入回收站，用于上传结果不理想时清理） */
async function handleDeleteSuccessDocs() {
  if (!result.value || result.value.successIds.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认删除本次上传成功的 ${result.value.successIds.length} 篇文献？此操作将移入回收站。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch { return }

  deletingFailed.value = true
  try {
    await batchDeleteDocuments({ documentIds: result.value.successIds })
    ElMessage.success('已删除本次上传的文献')
    result.value = null
  } catch {
    ElMessage.error('删除失败')
  } finally {
    deletingFailed.value = false
  }
}

/** 导出失败文档的Excel（方便用户检查问题后重新上传） */
function handleExportFailedExcel() {
  if (!result.value || result.value.failList.length === 0) return

  // 生成Excel兼容的HTML表格（.xls格式，Excel可直接打开）
  const headers = ['文件名', '标题', '作者', '关键词', '摘要', '发表时间', '来源期刊', 'DOI', 'RAG来源', '失败原因']
  const rows = result.value.failList.map(item => {
    return [
      item.fileName || '',
      item.title || '',
      item.authors || '',
      item.keywords || '',
      item.abstractText || '',
      item.publishDate || '',
      item.sourceJournal || '',
      item.doi || '',
      item.ragSource != null ? String(item.ragSource) : '',
      item.reason || ''
    ]
  })

  // 生成Excel兼容的HTML表格
  const html = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
<head><meta charset="UTF-8"><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>失败列表</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]--></head>
<body><table border="1"><thead><tr>${headers.map(h => `<th style="background:#4a5568;color:#fff;padding:6px;">${h}</th>`).join('')}</tr></thead>
<tbody>${rows.map(row => `<tr>${row.map(cell => `<td style="padding:4px;mso-number-format:'\\@';">${String(cell).replace(/</g, '&lt;').replace(/>/g, '&gt;')}</td>`).join('')}</tr>`).join('')}</tbody>
</table></body></html>`

  const blob = new Blob(['\uFEFF' + html], { type: 'application/vnd.ms-excel;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `上传失败文献_${new Date().toISOString().slice(0, 10)}.xls`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出失败文献Excel，修改后可直接用于重新上传')
}

/** 清除成功导入的文档（仅清除显示，不删除文件，方便聚焦失败项） */
function handleClearSuccess() {
  if (!result.value) return
  result.value = {
    ...result.value,
    successCount: 0,
    successIds: [],
  }
  ElMessage.success('已清除成功导入的记录')
}

/** 清除全部结果（成功+失败都清除） */
function handleClearAll() {
  result.value = null
  ElMessage.success('已清除全部上传记录')
}

function applyQueryFolder() {
  const q = route.query.folderId
  if (q) {
    folderId.value = q // 保持字符串类型，与 el-tree-select 一致
  }
}

onMounted(async () => {
  if (folderStore.tree.length === 0) {
    await folderStore.fetchTree()
  }
  applyQueryFolder()
})

watch(() => route.query.folderId, () => applyQueryFolder())
watch(() => folderStore.tree.length, () => {
  if (route.query.folderId && !currentFolderName.value) {
    applyQueryFolder()
  }
})
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">批量上传文献</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">
        通过 Excel + 文件列表批量上传文献到指定目录
      </p>
    </div>

    <div class="card p-6 space-y-5">
      <!-- 目录 -->
      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">
          目标目录 *
          <span v-if="currentFolderName" class="ml-2 px-2 py-0.5 rounded text-xs" style="background: var(--bg-hover); color: var(--text-secondary)">
            📁 {{ currentFolderName }}
          </span>
        </label>
        <el-tree-select
          v-model="folderId"
          :data="folderStore.tree"
          :props="{ label: 'folderName', value: 'id', children: 'children' }"
          placeholder="选择目录"
          check-strictly
          class="w-full"
        />
      </div>

      <!-- DOI 导入 -->
      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">
          <el-icon :size="14" class="inline-block mr-1"><Download /></el-icon> DOI 一键导入
        </label>
        <p class="text-xs mb-2" style="color: var(--text-muted)">
          上传一个仅含 DOI 号的 Excel（第一列），系统自动通过 Crossref 查询文献元数据并返回可直接批量导入的 Excel
        </p>
        <el-upload :auto-upload="false" :limit="1" :on-change="handleDoiImport" drag class="w-full" accept=".xlsx,.xls" :disabled="doiParsing">
          <el-icon :size="36" class="text-blue-400 mb-2"><Download /></el-icon>
          <div class="text-sm" style="color: var(--text-muted)">
            <template v-if="doiParsing">正在查询 DOI 元数据...</template>
            <template v-else>点击或拖拽 DOI Excel，自动下载填充后的批量导入表</template>
          </div>
        </el-upload>
      </div>

      <!-- Excel -->
      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">Excel 元数据文件 *</label>
        <p class="text-xs mb-2" style="color: var(--text-muted)">
          Excel 需包含以下列（第一行为表头）：
        </p>
        <div class="mb-3 p-3 rounded-lg text-xs overflow-x-auto" style="background: var(--bg-hover); font-family: monospace">
          <table class="w-full border-collapse">
            <thead>
              <tr>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">文件名</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">标题</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">作者</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">关键词</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">摘要</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">发表时间</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">来源期刊</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">DOI</th>
                <th class="px-2 py-1 text-left border" style="border-color: var(--border-color)">RAG来源</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">paper1.pdf</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">深度学习综述</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">张三</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">深度学习,AI</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">本文综述...</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">2024-01-15</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">计算机学报</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">10.1234/example</td>
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">1</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="text-xs mb-2" style="color: var(--text-muted)">* 文件名列与上传文件名模糊匹配（去除标点后比较前20字符）；RAG来源：0=普通文献，1=外部知识源；未匹配的文件将标记为失败，不会自动上传</p>
        <el-upload :auto-upload="false" :limit="1" :on-change="handleExcelChange" drag class="w-full" accept=".xlsx,.xls">
          <el-icon :size="36" class="text-emerald-300 mb-2"><Document /></el-icon>
          <div class="text-sm" style="color: var(--text-muted)">点击或拖拽 Excel 文件</div>
        </el-upload>
      </div>

      <!-- 文献文件 -->
      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">文献文件 *（可多选）</label>
        <el-upload :auto-upload="false" multiple :on-change="handleFilesChange" drag class="w-full">
          <el-icon :size="36" class="text-slate-300 mb-2"><UploadFilled /></el-icon>
          <div class="text-sm" style="color: var(--text-muted)">拖拽文件或 <em class="text-primary-500 not-italic">点击选择</em></div>
          <template #tip><div class="text-xs mt-1" style="color: var(--text-muted)">支持 PDF、DOCX、PPT、MD 等格式，文件名需与 Excel 中匹配</div></template>
        </el-upload>
      </div>

      <div class="flex items-center gap-3 pt-2">
        <el-button type="primary" size="large" :loading="uploading" :disabled="!excelFile || docFiles.length === 0 || !folderId" @click="handleUpload">
          <el-icon :size="16"><UploadFilled /></el-icon> 批量上传
        </el-button>
        <el-button size="large" @click="router.back()">取消</el-button>
      </div>

      <!-- 结果 -->
      <div v-if="result" class="mt-4 p-4 rounded-lg" style="background: var(--bg-hover)">
        <div class="flex items-center gap-2 text-sm flex-wrap">
          <span class="text-emerald-500 font-semibold">成功: {{ result.successCount }}</span>
          <span class="text-red-500 font-semibold">失败: {{ result.failCount }}</span>
          <div class="flex-1" />
          <el-button
            v-if="result.failList.length > 0"
            size="small"
            type="warning"
            plain
            @click="handleExportFailedExcel"
          >
            导出失败Excel
          </el-button>
          <el-button
            v-if="result.successIds.length > 0"
            size="small"
            type="danger"
            plain
            :loading="deletingFailed"
            @click="handleDeleteSuccessDocs"
          >
            撤销上传 (删{{ result.successIds.length }}篇)
          </el-button>
          <el-button
            v-if="result.successIds.length > 0"
            size="small"
            type="info"
            plain
            @click="handleClearSuccess"
          >
            清除成功
          </el-button>
          <el-button
            size="small"
            type="info"
            plain
            @click="handleClearAll"
          >
            清除全部
          </el-button>
        </div>
        <div v-if="result.failList.length > 0" class="mt-3 space-y-1">
          <p class="text-xs font-medium" style="color: var(--text-muted)">失败详情：</p>
          <div v-for="(item, idx) in result.failList" :key="idx" class="text-xs" style="color: var(--text-secondary)">
            <span class="text-red-500">{{ item.fileName }}</span> — {{ item.reason }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
