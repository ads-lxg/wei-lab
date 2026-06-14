<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserPage, updateUserStatus, assignUserRoles, deleteUser, updateUserInfo } from '@/api/user'
import { listAllRoles } from '@/api/role'
import type { UserVO, SysRole } from '@/types'
import type { PageResult } from '@/types'

const loading = ref(false)
const users = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const roles = ref<SysRole[]>([])

const roleDialogVisible = ref(false)
const editDialogVisible = ref(false)
const selectedUser = ref<UserVO | null>(null)
const selectedRoleIds = ref<number[]>([])

// Stats
const stats = ref({ total: 0, active: 0, disabled: 0, admin: 0 })

async function fetchData() {
  loading.value = true
  try {
    const result: PageResult<UserVO> = await getUserPage({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    users.value = result.records
    total.value = result.total
    // Calculate stats
    stats.value.total = result.total
    stats.value.active = users.value.filter(u => u.status === 1).length
    stats.value.disabled = users.value.filter(u => u.status === 0).length
    stats.value.admin = users.value.filter(u => u.roles?.includes('ADMIN')).length
  } finally {
    loading.value = false
  }
}

async function handleToggleStatus(user: UserVO) {
  const newStatus = user.status === 1 ? 0 : 1
  await updateUserStatus(user.id, newStatus)
  ElMessage.success('状态已更新')
  fetchData()
}

function openRoleDialog(user: UserVO) {
  selectedUser.value = user
  selectedRoleIds.value = user.roles?.map((r) => roles.value.find((role) => role.roleCode === r)?.id || 0).filter(Boolean) || []
  roleDialogVisible.value = true
}

async function handleAssignRoles() {
  if (!selectedUser.value) return
  await assignUserRoles(selectedUser.value.id, selectedRoleIds.value)
  ElMessage.success('角色分配成功')
  roleDialogVisible.value = false
  fetchData()
}

function openEditDialog(user: UserVO) {
  selectedUser.value = { ...user }
  editDialogVisible.value = true
}

async function handleUpdateInfo() {
  if (!selectedUser.value) return
  await updateUserInfo(selectedUser.value.id, {
    realName: selectedUser.value.realName,
    email: selectedUser.value.email,
    phone: selectedUser.value.phone,
  })
  ElMessage.success('信息更新成功')
  editDialogVisible.value = false
  fetchData()
}

async function handleDelete(user: UserVO) {
  try {
    await ElMessageBox.confirm(`确认删除用户 "${user.username}"？`, '删除用户', { type: 'warning' })
    await deleteUser(user.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* cancelled */ }
}

onMounted(async () => {
  roles.value = await listAllRoles()
  fetchData()
})

function roleTagType(role: string): string {
  const r = role.toUpperCase()
  if (r === 'ADMIN') return 'danger'
  if (r === 'TEACHER') return 'warning'
  if (r === 'STUDENT') return 'success'
  if (r === 'GUEST') return 'info'
  return ''
}
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <!-- Admin Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">用户管理</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">管理系统中的所有用户账号、角色与权限</p>
      </div>
    </div>

    <!-- Admin Stats Bar -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="card p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-500/20 flex items-center justify-center text-primary-500 shrink-0">
          <el-icon :size="18"><User /></el-icon>
        </div>
        <div>
          <p class="text-xs" style="color: var(--text-muted)">用户总数</p>
          <p class="text-xl font-bold" style="color: var(--text-primary)">{{ total }}</p>
        </div>
      </div>
      <div class="card p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-500/20 flex items-center justify-center text-emerald-500 shrink-0">
          <el-icon :size="18"><CircleCheck /></el-icon>
        </div>
        <div>
          <p class="text-xs" style="color: var(--text-muted)">启用</p>
          <p class="text-xl font-bold" style="color: var(--text-primary)">{{ stats.active }}</p>
        </div>
      </div>
      <div class="card p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-red-50 dark:bg-red-500/20 flex items-center justify-center text-red-500 shrink-0">
          <el-icon :size="18"><RemoveFilled /></el-icon>
        </div>
        <div>
          <p class="text-xs" style="color: var(--text-muted)">禁用</p>
          <p class="text-xl font-bold" style="color: var(--text-primary)">{{ stats.disabled }}</p>
        </div>
      </div>
      <div class="card p-4 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-500/20 flex items-center justify-center text-amber-500 shrink-0">
          <el-icon :size="18"><Key /></el-icon>
        </div>
        <div>
          <p class="text-xs" style="color: var(--text-muted)">管理员</p>
          <p class="text-xl font-bold" style="color: var(--text-primary)">{{ stats.admin }}</p>
        </div>
      </div>
    </div>

    <!-- User Table -->
    <div class="card">
      <div class="flex items-center gap-2 p-3 border-b" style="border-color: var(--border-color)">
        <el-input v-model="keyword" placeholder="搜索用户..." size="small" class="max-w-[240px]" :prefix-icon="Search" clearable @clear="fetchData" @keyup.enter="fetchData" />
        <el-button size="small" @click="fetchData">搜索</el-button>
        <div class="flex-1" />
        <el-tag size="small" effect="plain">共 {{ total }} 位用户</el-tag>
      </div>

      <el-table :data="users" v-loading="loading" size="small" stripe row-key="id" style="width: 100%">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-2.5">
              <el-avatar :size="32" :src="row.avatar">{{ (row.realName || row.username || 'U').charAt(0).toUpperCase() }}</el-avatar>
              <div>
                <p class="text-sm font-medium" style="color: var(--text-primary)">{{ row.realName || row.username }}</p>
                <p class="text-xs" style="color: var(--text-muted)">@{{ row.username }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机" width="120">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r" size="small" class="mr-1" :type="roleTagType(r)">{{ r }}</el-tag>
            <span v-if="!row.roles?.length" class="text-xs" style="color: var(--text-muted)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="handleToggleStatus(row)"
              size="small"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
            />
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="130" align="center">
          <template #default="{ row }">{{ row.createTime?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openRoleDialog(row)">角色</el-button>
            <el-button size="small" link @click="openEditDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除此用户？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-between items-center px-4 py-3 border-t" style="border-color: var(--border-color)">
        <span class="text-xs" style="color: var(--text-muted)">第 {{ page }} 页 · 共 {{ total }} 条</span>
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" :total="total" background small layout="total, sizes, prev, pager, next" @current-change="fetchData" @size-change="fetchData" />
      </div>
    </div>

    <!-- Role Dialog -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="420px">
      <div v-if="selectedUser" class="mb-3 text-sm" style="color: var(--text-muted)">
        用户：<strong style="color: var(--text-primary)">{{ selectedUser.realName || selectedUser.username }}</strong>
      </div>
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="role in roles" :key="role.id" class="py-1.5">
          <el-checkbox :value="role.id" :label="role.roleName" />
          <span class="text-xs ml-1" style="color: var(--text-muted)">({{ role.roleCode }})</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑用户信息" width="420px">
      <el-form v-if="selectedUser" label-width="80px" size="small">
        <el-form-item label="真实姓名"><el-input v-model="selectedUser.realName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="selectedUser.email" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="selectedUser.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateInfo">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
