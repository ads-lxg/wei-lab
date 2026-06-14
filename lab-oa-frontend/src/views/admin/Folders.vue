<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete, Refresh, FolderOpened, Folder } from '@element-plus/icons-vue'
import { useFolderStore } from '@/stores/folder'
import type { FolderTreeVO } from '@/types'

const folderStore = useFolderStore()

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'rename'>('create')
const formName = ref('')
const formParentId = ref<number>(0)
const currentNode = ref<FolderTreeVO | null>(null)

async function fetchTree() { await folderStore.fetchTree() }

function openCreate(parentId: number | null) {
  dialogMode.value = 'create'
  formName.value = ''
  formParentId.value = parentId ?? 0
  currentNode.value = null
  dialogVisible.value = true
}

function openRename(node: FolderTreeVO) {
  dialogMode.value = 'rename'
  formName.value = node.folderName
  currentNode.value = node
  dialogVisible.value = true
}

async function handleSave() {
  if (!formName.value.trim()) { ElMessage.warning('请输入目录名'); return }
  try {
    if (dialogMode.value === 'create') {
      await folderStore.doCreate({ parentId: formParentId.value, folderName: formName.value })
      ElMessage.success('创建成功')
    } else if (dialogMode.value === 'rename' && currentNode.value) {
      await folderStore.doUpdate({ id: currentNode.value.id, folderName: formName.value })
      ElMessage.success('重命名成功')
    }
    dialogVisible.value = false
  } catch { /* handled */ }
}

async function handleDelete(node: FolderTreeVO) {
  try {
    await ElMessageBox.confirm(`确认删除目录 "${node.folderName}"？子目录也将被删除。`, '删除目录', { type: 'warning' })
    await folderStore.doDelete(node.id)
    ElMessage.success('删除成功')
  } catch { /* cancelled */ }
}

/** 拖拽移动目录 */
async function handleDrop(draggingNode: any, dropNode: any, dropType: any) {
  let targetParentId: number
  if (dropType === 'inner') {
    targetParentId = dropNode.data.id
  } else {
    targetParentId = dropNode.data.parentId || 0
  }

  if (draggingNode.data.id === targetParentId) {
    ElMessage.warning('不能将目录移动到自身下')
    await fetchTree()
    return
  }

  try {
    await folderStore.doMove({ folderId: draggingNode.data.id, targetParentId })
    ElMessage.success('移动成功')
  } catch {
    ElMessage.error('移动失败')
    await fetchTree()
  }
}

/** 防止将父节点拖入自己的子节点 */
function allowDrop(draggingNode: any, dropNode: any, type: any) {
  if (type === 'inner') {
    const targetId = dropNode.data.id
    let parent: any = draggingNode.parent
    while (parent) {
      if (parent.data?.id === targetId) return false
      parent = parent.parent
    }
  }
  return true
}

function allowDrag() {
  return true
}

onMounted(fetchTree)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">目录管理</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">管理文献分类目录树 — 创建、重命名、删除、拖拽移动</p>
      </div>
      <div class="flex gap-2">
        <el-button type="primary" @click="openCreate(null)"><el-icon :size="14"><Plus /></el-icon> 新建根目录</el-button>
        <el-button @click="fetchTree"><el-icon :size="14"><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="card p-5">
      <el-tree
        :data="folderStore.tree"
        node-key="id"
        :props="{ label: 'folderName', children: 'children' }"
        :expand-on-click-node="false"
        default-expand-all
        highlight-current
        draggable
        :allow-drop="allowDrop"
        :allow-drag="allowDrag"
        @node-drop="handleDrop"
      >
        <template #default="{ data }">
          <div class="flex items-center justify-between w-full pr-2" style="height: 34px">
            <span class="flex items-center gap-2 text-sm flex-1 truncate">
              <el-icon :size="14"><FolderOpened v-if="data.children?.length" /><Folder v-else /></el-icon>
              <span style="color: var(--text-primary)">{{ data.folderName }}</span>
              <span class="text-xs" style="color: var(--text-muted)">({{ data.children?.length || 0 }})</span>
            </span>
            <span class="flex items-center gap-1 shrink-0">
              <el-button size="small" text @click.stop="openCreate(data.id)">
                <el-icon :size="14"><Plus /></el-icon>
              </el-button>
              <el-button size="small" text @click.stop="openRename(data)">
                <el-icon :size="14"><Edit /></el-icon>
              </el-button>
              <el-popconfirm title="确认删除此目录？" @confirm="handleDelete(data)">
                <template #reference>
                  <el-button size="small" text @click.stop>
                    <el-icon :size="14" style="color: var(--text-muted)"><Delete /></el-icon>
                  </el-button>
                </template>
              </el-popconfirm>
            </span>
          </div>
        </template>
      </el-tree>

      <div v-if="!folderStore.tree.length" class="text-center py-10" style="color: var(--text-muted)">
        暂无目录，点击"新建根目录"开始创建
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建目录' : '重命名'" width="400px">
      <el-input v-model="formName" placeholder="目录名称" size="large" @keyup.enter="handleSave" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :disabled="!formName.trim()">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
