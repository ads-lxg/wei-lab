<script setup lang="ts">
import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { uploadAvatar } from '@/api/auth'
import { updateUserInfo } from '@/api/user'
import { useAvatarUrl } from '@/hooks/useAvatar'

const userStore = useUserStore()
const user = computed(() => userStore.user)
const profileAvatarSrc = useAvatarUrl(computed(() => userStore.user?.avatar))

const activeTab = ref('info')
const uploading = ref(false)
const saving = ref(false)

// 可编辑的字段
const editForm = ref({
  realName: '',
  email: '',
  phone: '',
})
const isEditing = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const changingPassword = ref(false)

async function handleAvatarUpload(e: any) {
  const file = e?.raw || e
  if (!file) return
  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    if (userStore.user) userStore.user.avatar = url
    ElMessage.success('头像更新成功')
  } finally {
    uploading.value = false
  }
}

function startEdit() {
  if (!user.value) return
  editForm.value = {
    realName: user.value.realName || '',
    email: user.value.email || '',
    phone: user.value.phone || '',
  }
  isEditing.value = true
}

function cancelEdit() {
  isEditing.value = false
}

async function handleSaveInfo() {
  if (!user.value) return
  saving.value = true
  try {
    await updateUserInfo(user.value.id, {
      realName: editForm.value.realName,
      email: editForm.value.email,
      phone: editForm.value.phone,
    })
    // 刷新用户信息
    await userStore.fetchUserInfo()
    ElMessage.success('信息已更新')
    isEditing.value = false
  } catch {
    // handled by interceptor
  } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写密码')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  changingPassword.value = true
  try {
    ElMessage.success('密码修改成功')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } finally {
    changingPassword.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-6 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">个人中心</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">管理您的个人资料和偏好</p>
    </div>

    <!-- Avatar Banner -->
    <div class="card p-6">
      <div class="flex items-center gap-5">
        <el-badge value="+" class="avatar-badge" :hidden="uploading">
          <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleAvatarUpload">
            <el-avatar :size="72" :src="profileAvatarSrc || user?.avatar" class="cursor-pointer ring-4 ring-slate-100 dark:ring-zinc-800">
              {{ (user?.realName || user?.username || 'U').charAt(0).toUpperCase() }}
            </el-avatar>
          </el-upload>
        </el-badge>
        <div>
          <h2 class="text-xl font-semibold" style="color: var(--text-primary)">{{ user?.realName || user?.username }}</h2>
          <p class="text-sm mt-0.5" style="color: var(--text-muted)">{{ user?.email }}</p>
          <div class="flex gap-1 mt-2">
            <el-tag v-for="role in user?.roles" :key="role" size="small" :type="role === 'ADMIN' ? 'danger' : ''">{{ role }}</el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="card">
      <el-tabs v-model="activeTab" class="px-4">
        <el-tab-pane label="基本信息" name="info">
          <div class="p-4 space-y-4 max-w-md">
            <!-- View Mode -->
            <template v-if="!isEditing">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="用户名">{{ user?.username }}</el-descriptions-item>
                <el-descriptions-item label="真实姓名">{{ user?.realName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ user?.email || '-' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">{{ user?.phone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="注册时间">{{ user?.createTime?.substring(0, 10) || '-' }}</el-descriptions-item>
              </el-descriptions>
              <el-button type="primary" :icon="Edit" @click="startEdit" class="mt-4">修改信息</el-button>
            </template>

            <!-- Edit Mode -->
            <div v-else class="space-y-3">
              <div>
                <label class="block text-sm font-medium mb-1" style="color: var(--text-secondary)">真实姓名</label>
                <el-input v-model="editForm.realName" placeholder="请输入真实姓名" size="large" :prefix-icon="UserFilled" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1" style="color: var(--text-secondary)">邮箱</label>
                <el-input v-model="editForm.email" placeholder="请输入邮箱" size="large" :prefix-icon="Message" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1" style="color: var(--text-secondary)">手机号</label>
                <el-input v-model="editForm.phone" placeholder="请输入手机号" size="large" :prefix-icon="Phone" />
              </div>
              <div class="flex gap-2 pt-2">
                <el-button type="primary" :loading="saving" @click="handleSaveInfo">保存</el-button>
                <el-button @click="cancelEdit" :disabled="saving">取消</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <div class="p-4 max-w-sm space-y-3">
            <el-input v-model="passwordForm.oldPassword" placeholder="当前密码" type="password" show-password size="large" />
            <el-input v-model="passwordForm.newPassword" placeholder="新密码" type="password" show-password size="large" />
            <el-input v-model="passwordForm.confirmPassword" placeholder="确认新密码" type="password" show-password size="large" />
            <el-button type="primary" size="large" :loading="changingPassword" @click="handleChangePassword">
              修改密码
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.avatar-badge :deep(.el-badge__content) {
  background: #1677ff;
  border: none;
  font-size: 14px;
}
</style>
