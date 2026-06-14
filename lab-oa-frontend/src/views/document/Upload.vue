<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFolderStore } from '@/stores/folder'
import { uploadDocument } from '@/api/document'

const router = useRouter()
const route = useRoute()
const folderStore = useFolderStore()

const folderId = ref<number | string | null>(null)
const title = ref('')
const authors = ref('')
const keywords = ref('')
const abstractText = ref('')
const sourceJournal = ref('')
const doi = ref('')
const ragSource = ref(0)
const file = ref<File | null>(null)
const uploading = ref(false)

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

function handleFileChange(f: any) {
  file.value = f?.raw || f
}

async function handleUpload() {
  if (!file.value || !folderId.value) {
    ElMessage.warning('请选择文件和目录')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('folderId', String(folderId.value))
    if (title.value) fd.append('title', title.value)
    if (authors.value) fd.append('authors', authors.value)
    if (keywords.value) fd.append('keywords', keywords.value)
    if (abstractText.value) fd.append('abstractText', abstractText.value)
    if (sourceJournal.value) fd.append('sourceJournal', sourceJournal.value)
    if (doi.value) fd.append('doi', doi.value)
    fd.append('ragSource', String(ragSource.value))

    const result = await uploadDocument(fd)
    ElMessage.success('上传成功')
    router.push(`/document/detail/${result.id}`)
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
// 当树加载完成后重新匹配
watch(() => folderStore.tree.length, () => {
  if (route.query.folderId && !currentFolderName.value) {
    applyQueryFolder()
  }
})
</script>

<template>
  <div class="max-w-2xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">上传文献</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">上传文献到指定目录</p>
    </div>

    <div class="card p-6 space-y-4">
      <!-- 目标目录（自动填充） -->
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

      <!-- ragSource -->
      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">文献来源</label>
        <el-radio-group v-model="ragSource" size="small">
          <el-radio :value="0">普通文献</el-radio>
          <el-radio :value="1">外部知识源 (RAG)</el-radio>
        </el-radio-group>
      </div>

      <div>
        <label class="block text-sm font-medium mb-1.5" style="color: var(--text-secondary)">文件 *</label>
        <el-upload :auto-upload="false" :limit="1" :on-change="handleFileChange" drag class="w-full">
          <el-icon :size="36" class="text-slate-300 mb-2"><UploadFilled /></el-icon>
          <div class="text-sm" style="color: var(--text-muted)">拖拽文件到这里，或 <em class="text-primary-500 not-italic">点击上传</em></div>
          <template #tip><div class="text-xs mt-1" style="color: var(--text-muted)">支持 PDF、DOCX、PPT、MD 等格式</div></template>
        </el-upload>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <el-input v-model="title" placeholder="标题（选填）" size="large" />
        <el-input v-model="authors" placeholder="作者（选填）" size="large" />
        <el-input v-model="keywords" placeholder="关键词，逗号分隔（选填）" size="large" />
        <el-input v-model="sourceJournal" placeholder="来源期刊（选填）" size="large" />
        <el-input v-model="doi" placeholder="DOI（选填）" size="large" />
      </div>
      <el-input v-model="abstractText" placeholder="摘要（选填）" type="textarea" :rows="3" />

      <div class="flex items-center gap-3 pt-2">
        <el-button type="primary" size="large" :loading="uploading" :disabled="!file || !folderId" @click="handleUpload">
          <el-icon :size="16"><Upload /></el-icon> 上传文献
        </el-button>
        <el-button size="large" @click="router.back()">取消</el-button>
      </div>
    </div>
  </div>
</template>
