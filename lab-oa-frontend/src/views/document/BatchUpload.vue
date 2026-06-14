<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFolderStore } from '@/stores/folder'
import { batchUploadDocuments } from '@/api/document'
import type { BatchUploadResultVO } from '@/types'

const router = useRouter()
const route = useRoute()
const folderStore = useFolderStore()

const folderId = ref<number | string | null>(null)
const excelFile = ref<File | null>(null)
const docFiles = ref<File[]>([])
const uploading = ref(false)
const result = ref<BatchUploadResultVO | null>(null)

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
    ElMessage.success(`上传完成：成功 ${res.successCount}，失败 ${res.failCount}`)
  } finally {
    uploading.value = false
  }
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
                <td class="px-2 py-1 border" style="border-color: var(--border-color); color: var(--text-muted)">0</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="text-xs mb-2" style="color: var(--text-muted)">* 文件名列必须与上传的文献文件名完全一致；RAG来源：0=普通文献，1=外部知识源</p>
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
          <template #tip><div class="text-xs mt-1" style="color: var(--text-muted)">支持 PDF、DOCX、PPT、MD 等格式，文件名需与 Excel 中一致</div></template>
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
        <div class="flex items-center gap-4 text-sm">
          <span class="text-emerald-500 font-semibold">成功: {{ result.successCount }}</span>
          <span class="text-red-500 font-semibold">失败: {{ result.failCount }}</span>
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
