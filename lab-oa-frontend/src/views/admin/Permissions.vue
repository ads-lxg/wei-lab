<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAllPermissions, getRolePermissions, setRolePermissions } from '@/api/permission'
import { listAllRoles } from '@/api/role'
import { verifyPassword } from '@/api/auth'
import type { SysPermission, SysRole } from '@/types'

const loading = ref(false)
const roles = ref<SysRole[]>([])
const permissions = ref<SysPermission[]>([])
const selectedRoleId = ref<number | null>(null)
const checkedPermIds = ref<number[]>([])
const selectAll = ref(false)

// 二次密码验证
const passwordVerified = ref(false)
const passwordDialogVisible = ref(true)
const verifyPasswordInput = ref('')
const verifyLoading = ref(false)

async function handleVerifyPassword() {
  if (!verifyPasswordInput.value) {
    ElMessage.warning('请输入密码')
    return
  }
  verifyLoading.value = true
  try {
    await verifyPassword(verifyPasswordInput.value)
    passwordVerified.value = true
    passwordDialogVisible.value = false
    ElMessage.success('验证成功')
  } catch {
    // error shown by interceptor
  } finally {
    verifyLoading.value = false
  }
}

async function fetchPermissions() {
  permissions.value = await listAllPermissions()
}

async function onRoleChange(roleId: number) {
  checkedPermIds.value = await getRolePermissions(roleId)
  updateSelectAll()
}

async function handleSavePermissions() {
  if (!selectedRoleId.value) return
  await setRolePermissions(selectedRoleId.value, checkedPermIds.value)
  ElMessage.success('权限保存成功')
}

function updateSelectAll() {
  selectAll.value = permissions.value.length > 0 && checkedPermIds.value.length === permissions.value.length
}

function toggleAll(val: boolean) {
  checkedPermIds.value = val ? permissions.value.map(p => p.id) : []
  selectAll.value = val
}

function togglePerm(permId: number) {
  updateSelectAll()
}

onMounted(async () => {
  roles.value = await listAllRoles()
  fetchPermissions()
})
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">权限管理</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">为每个角色分配对应的操作权限</p>
    </div>

    <!-- 二次密码验证对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="身份验证" width="380px" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <p class="text-sm mb-4" style="color: var(--text-muted)">进入权限管理界面需要再次输入密码进行身份验证</p>
      <el-input v-model="verifyPasswordInput" type="password" placeholder="请输入管理员密码" show-password @keyup.enter="handleVerifyPassword" />
      <template #footer>
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="verifyLoading" @click="handleVerifyPassword">验证</el-button>
      </template>
    </el-dialog>

    <div v-if="!passwordVerified" class="card p-16 text-center">
      <el-icon :size="48" style="color: var(--text-muted); opacity: 0.3"><Lock /></el-icon>
      <p class="text-sm mt-4" style="color: var(--text-muted)">请先完成身份验证</p>
    </div>

    <template v-else>

    <div class="card p-5">
      <!-- Role Selector -->
      <div class="flex items-center gap-4 pb-4 border-b" style="border-color: var(--border-color)">
        <span class="text-sm font-medium" style="color: var(--text-secondary)">选择角色</span>
        <el-select v-model="selectedRoleId" placeholder="选择一个角色" @change="onRoleChange" size="small" style="width: 220px">
          <el-option v-for="role in roles" :key="role.id" :label="`${role.roleName} (${role.roleCode})`" :value="role.id" />
        </el-select>
        <div class="flex-1" />
        <el-button v-if="selectedRoleId" type="primary" size="small" @click="handleSavePermissions">
          <el-icon :size="14"><Check /></el-icon> 保存权限
        </el-button>
      </div>

      <div v-if="selectedRoleId" class="pt-4">
        <!-- Select All -->
        <div class="flex items-center gap-3 mb-3 px-1">
          <el-checkbox :model-value="selectAll" :indeterminate="checkedPermIds.length > 0 && !selectAll" @change="toggleAll" />
          <span class="text-sm font-medium" style="color: var(--text-secondary)">全选 / 取消全选</span>
          <span class="text-xs" style="color: var(--text-muted)">{{ checkedPermIds.length }} / {{ permissions.length }} 项</span>
        </div>

        <!-- Permission Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-1">
          <div
            v-for="perm in permissions" :key="perm.id"
            class="flex items-center gap-2 px-3 py-2.5 rounded-lg hover:bg-slate-50 dark:hover:bg-zinc-800/50 transition-colors cursor-pointer"
            @click="checkedPermIds.includes(perm.id) ? checkedPermIds = checkedPermIds.filter(id => id !== perm.id) : checkedPermIds.push(perm.id); togglePerm(perm.id)"
          >
            <el-checkbox :model-value="checkedPermIds.includes(perm.id)" class="pointer-events-none" />
            <div class="flex-1 min-w-0">
              <p class="text-sm truncate" style="color: var(--text-primary)">{{ perm.permName }}</p>
            </div>
            <code class="text-[11px] px-1.5 py-0.5 rounded" style="background: var(--bg-hover); color: var(--text-muted)">{{ perm.permCode }}</code>
          </div>
        </div>
      </div>

      <div v-else class="text-center py-16">
        <el-icon :size="48" style="color: var(--text-muted); opacity: 0.3"><Lock /></el-icon>
        <p class="text-sm mt-3" style="color: var(--text-muted)">请选择一个角色以管理其权限</p>
      </div>
    </div>
    </template>
  </div>
</template>
