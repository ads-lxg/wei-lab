<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAllRoles, createRole, updateRole, deleteRole } from '@/api/role'
import type { SysRole } from '@/types'

const loading = ref(false)
const roles = ref<SysRole[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<Partial<SysRole>>({})

function openCreate() {
  isEdit.value = false
  form.value = { roleCode: '', roleName: '', description: '' }
  dialogVisible.value = true
}

function openEdit(role: SysRole) {
  isEdit.value = true
  form.value = { ...role }
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value && form.value.id) {
    await updateRole(form.value.id, form.value)
    ElMessage.success('更新成功')
  } else {
    await createRole(form.value)
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleDelete(role: SysRole) {
  try {
    await ElMessageBox.confirm(`确认删除角色 "${role.roleName}"？`, '删除角色', { type: 'warning' })
    await deleteRole(role.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

async function fetchData() {
  loading.value = true
  try {
    roles.value = await listAllRoles()
  } finally {
    loading.value = false
  }
}

const roleColors: Record<string, string> = {
  admin: 'danger',
  teacher: 'warning',
  student: 'success',
  guest: 'info',
  ADMIN: 'danger',
  TEACHER: 'warning',
  STUDENT: 'success',
  GUEST: 'info',
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">角色管理</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">定义系统角色，控制不同角色的访问权限</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon :size="14"><Plus /></el-icon> 新建角色
      </el-button>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="card p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
          <el-icon :size="18"><Avatar /></el-icon>
        </div>
        <div>
          <p class="text-xs" style="color: var(--text-muted)">角色总数</p>
          <p class="text-xl font-bold" style="color: var(--text-primary)">{{ roles.length }}</p>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="card">
      <el-table :data="roles" v-loading="loading" size="small" stripe row-key="id" style="width: 100%">
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <div class="flex items-center gap-2.5">
              <el-tag :type="roleColors[row.roleCode] || 'info'" size="small" effect="dark">{{ row.roleCode }}</el-tag>
              <span class="text-sm font-medium" style="color: var(--text-primary)">{{ row.roleName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="140" align="center">
          <template #default="{ row }">{{ row.createTime?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除此角色？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新建角色'" width="420px">
      <el-form size="small" label-width="80px">
        <el-form-item label="角色编码"><el-input v-model="form.roleCode" placeholder="如: TEACHER" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.roleName" placeholder="如: 教师" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" placeholder="角色描述" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
