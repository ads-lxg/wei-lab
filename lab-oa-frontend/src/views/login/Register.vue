<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getRandomQuestions, verifySecurityAnswers } from '@/api/security-question'
import type { RegisterDTO } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const form = reactive<RegisterDTO>({ email: '', username: '', password: '', realName: '', phone: '' })
const loading = ref(false)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref<string>('')

// ===== 安全问题 =====
interface SecurityQuestionItem {
  id: number
  question: string
}
const securityQuestions = ref<SecurityQuestionItem[]>([])
const securityAnswers = reactive<Record<number, string>>({})
const questionsLoading = ref(false)
const answerVerified = ref(false)        // 答案是否已验证通过
const answerVerifying = ref(false)       // 正在验证中
const answerError = ref('')              // 验证错误信息
const registerLocked = ref(false)        // 注册锁定（3次失败）
const lockMessage = ref('')              // 锁定提示信息

async function loadQuestions() {
  questionsLoading.value = true
  answerVerified.value = false
  answerError.value = ''
  // 清空所有旧答案
  securityQuestions.value.forEach(q => {
    delete securityAnswers[q.id]
  })
  try {
    securityQuestions.value = await getRandomQuestions()
    // 题库不足2题时，跳过安全验证，直接允许注册
    if (securityQuestions.value.length < 2) {
      answerVerified.value = true
    }
  } catch {
    // handled by interceptor
  } finally {
    questionsLoading.value = false
  }
}

/** 刷新题目（重新请求） */
function refreshQuestions() {
  if (registerLocked.value) return
  loadQuestions()
}

/** 是否有两个答案都填了 */
function canVerify() {
  const q1 = securityQuestions.value[0]
  const q2 = securityQuestions.value[1]
  if (!q1 || !q2) return false
  return !!(securityAnswers[q1.id] || '').trim() && !!(securityAnswers[q2.id] || '').trim()
}

/** 用户点击"验证答案"按钮，手动触发异步验证 */
async function handleVerifyAnswers() {
  if (answerVerified.value || registerLocked.value) return

  const q1 = securityQuestions.value[0]
  const q2 = securityQuestions.value[1]
  if (!q1 || !q2) return
  const ans1 = (securityAnswers[q1.id] || '').trim()
  const ans2 = (securityAnswers[q2.id] || '').trim()
  if (!ans1 || !ans2) {
    ElMessage.warning('请填写两个安全问题的答案')
    return
  }

  answerVerifying.value = true
  answerError.value = ''
  try {
    await verifySecurityAnswers({
      [q1.id]: ans1,
      [q2.id]: ans2,
    })
    answerVerified.value = true
    answerError.value = ''
  } catch (e: any) {
    answerVerified.value = false
    const msg = e?.message || '存在回答错误'
    answerError.value = msg
    if (msg.includes('锁定')) {
      registerLocked.value = true
      lockMessage.value = msg
    }
  } finally {
    answerVerifying.value = false
  }
}

onMounted(() => {
  loadQuestions()
})

// ===== 密码强度校验 =====
const passwordStrength = computed(() => {
  const pwd = form.password
  if (!pwd) return { level: 0, text: '', color: '' }
  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(pwd)) score++

  if (score <= 2) return { level: 1, text: '弱', color: '#ef4444' }
  if (score <= 4) return { level: 2, text: '中', color: '#f59e0b' }
  return { level: 3, text: '强', color: '#22c55e' }
})

const passwordErrors = computed(() => {
  const pwd = form.password
  if (!pwd) return []
  const errors: string[] = []
  if (pwd.length < 8) errors.push('至少8位')
  if (!/[a-z]/.test(pwd)) errors.push('需含小写字母')
  if (!/[A-Z]/.test(pwd)) errors.push('需含大写字母')
  if (!/\d/.test(pwd)) errors.push('需含数字')
  return errors
})

function validatePassword(): boolean {
  if (form.password.length < 8) {
    ElMessage.warning('密码长度不能少于8位')
    return false
  }
  if (!/[a-z]/.test(form.password)) {
    ElMessage.warning('密码必须包含小写字母')
    return false
  }
  if (!/[A-Z]/.test(form.password)) {
    ElMessage.warning('密码必须包含大写字母')
    return false
  }
  if (!/\d/.test(form.password)) {
    ElMessage.warning('密码必须包含数字')
    return false
  }
  return true
}

function validateEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function validatePhone(phone: string): boolean {
  return phone === '' || /^1[3-9]\d{9}$/.test(phone)
}

async function handleRegister() {
  if (!form.email || !form.username || !form.password) {
    ElMessage.warning('请填写必填项')
    return
  }
  if (!validateEmail(form.email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  if (!validatePhone(form.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (!validatePassword()) return

  // 安全问题验证检查
  if (!answerVerified.value) {
    ElMessage.warning('请先完成安全问题验证')
    return
  }
  if (registerLocked.value) {
    ElMessage.warning(lockMessage.value || '注册通道已锁定')
    return
  }

  loading.value = true
  try {
    await userStore.doRegister(form)
    // 注册后如果有头像文件，自动登录后上传
    if (avatarFile.value) {
      const { login } = await import('@/api/auth')
      const res = await login({ username: form.username, password: form.password })
      userStore.token = res.token
      userStore.user = res.user
      const { uploadAvatar } = await import('@/api/auth')
      await uploadAvatar(avatarFile.value)
      userStore.fetchUserInfo()
    }
    ElMessage.success('注册成功')
    router.replace('/login')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleAvatarChange(f: any) {
  const file = f?.raw || f
  if (!file) return
  avatarFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => { avatarPreview.value = e.target?.result as string }
  reader.readAsDataURL(file)
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-white dark:bg-zinc-950 p-8">
    <div class="w-full max-w-sm animate-slide-up">
      <div class="text-center mb-8">
        <svg class="w-12 h-12 mx-auto mb-4" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#1677FF"/>
          <path d="M7 10h18M7 16h14M7 22h10" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>
        </svg>
        <h2 class="text-2xl font-bold text-slate-800 dark:text-zinc-100">创建账号</h2>
        <p class="text-sm text-slate-500 dark:text-zinc-400 mt-1">注册账号以开始使用</p>
      </div>

      <el-form @submit.prevent="handleRegister" class="space-y-3">
        <!-- 头像上传 -->
        <div class="flex justify-center mb-2">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="handleAvatarChange"
          >
            <el-avatar
              :size="72"
              :src="avatarPreview"
              class="cursor-pointer ring-4 ring-slate-100 dark:ring-zinc-800 hover:ring-primary-200 dark:hover:ring-primary-800 transition-all"
            >
              <el-icon :size="32" class="text-slate-300"><Camera /></el-icon>
            </el-avatar>
          </el-upload>
        </div>
        <p class="text-center text-xs text-slate-400 -mt-1">点击上传头像（可选）</p>

        <el-form-item><el-input v-model="form.email" placeholder="邮箱" size="large" type="email" :prefix-icon="Message" /></el-form-item>
        <el-form-item><el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" /></el-form-item>

        <!-- 密码 + 强度指示 -->
        <el-form-item>
          <div class="w-full">
            <el-input v-model="form.password" placeholder="密码 (至少8位，含大小写字母和数字)" size="large" type="password" :prefix-icon="Lock" show-password />
            <div v-if="form.password" class="mt-2">
              <div class="flex items-center gap-1 mb-1">
                <div class="flex-1 h-1.5 rounded-full bg-slate-200 dark:bg-zinc-700 overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-300"
                    :style="{ width: (passwordStrength.level / 3 * 100) + '%', background: passwordStrength.color }"
                  />
                </div>
                <span class="text-xs font-medium" :style="{ color: passwordStrength.color }">{{ passwordStrength.text }}</span>
              </div>
              <div v-if="passwordErrors.length > 0" class="flex flex-wrap gap-1">
                <span v-for="err in passwordErrors" :key="err" class="text-[10px] px-1.5 py-0.5 rounded bg-red-50 text-red-500 dark:bg-red-900/20 dark:text-red-400">
                  {{ err }}
                </span>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item><el-input v-model="form.realName" placeholder="真实姓名 (选填)" size="large" :prefix-icon="UserFilled" /></el-form-item>
        <el-form-item><el-input v-model="form.phone" placeholder="手机号 (选填)" size="large" :prefix-icon="Iphone" /></el-form-item>

        <!-- 安全问题区域 -->
        <div v-if="!questionsLoading && securityQuestions.length >= 2" class="space-y-3 p-4 rounded-lg" style="background: var(--bg-hover)">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium" style="color: var(--text-secondary)">安全验证</span>
            <el-button size="small" text :loading="questionsLoading" @click="refreshQuestions" :disabled="registerLocked">
              <el-icon :size="14"><Refresh /></el-icon> 换题
            </el-button>
          </div>

          <div v-if="questionsLoading" class="text-center py-2">
            <span class="text-xs" style="color: var(--text-muted)">加载中...</span>
          </div>

          <template v-else>
            <!-- 问题1 -->
            <div>
              <p class="text-sm mb-1" style="color: var(--text-primary)">{{ securityQuestions[0]?.question }}</p>
              <el-input
                v-model="securityAnswers[securityQuestions[0]?.id ?? 0]"
                placeholder="请输入答案"
                size="small"
                :disabled="registerLocked || answerVerified"
              />
            </div>
            <!-- 问题2 -->
            <div>
              <p class="text-sm mb-1" style="color: var(--text-primary)">{{ securityQuestions[1]?.question }}</p>
              <el-input
                v-model="securityAnswers[securityQuestions[1]?.id ?? 0]"
                placeholder="请输入答案"
                size="small"
                :disabled="registerLocked || answerVerified"
              />
            </div>

            <!-- 验证按钮 + 状态 -->
            <div class="flex items-center gap-3">
              <el-button
                size="small"
                type="primary"
                :loading="answerVerifying"
                :disabled="!canVerify() || registerLocked || answerVerified"
                @click="handleVerifyAnswers"
              >
                {{ answerVerifying ? '验证中...' : '验证答案' }}
              </el-button>
              <span v-if="answerVerified" class="text-xs" style="color: #22c55e">
                <el-icon :size="12"><CircleCheck /></el-icon> 验证通过
              </span>
              <span v-else-if="answerError" class="text-xs" style="color: #ef4444">
                {{ answerError }}
              </span>
            </div>
          </template>
        </div>

        <!-- 题库为空时跳过验证提示 -->
        <div v-else-if="!questionsLoading && securityQuestions.length < 2" class="text-xs text-center py-2" style="color: var(--text-muted)">
          当前题库题目不足2道，已自动跳过安全验证
        </div>

        <el-button
          type="primary"
          size="large"
          class="w-full"
          :loading="loading"
          :disabled="!answerVerified || registerLocked"
          @click="handleRegister"
        >
          {{ registerLocked ? '注册通道已锁定' : loading ? '注册中...' : '注 册' }}
        </el-button>
      </el-form>

      <div class="mt-6 text-center text-sm text-slate-500 dark:text-zinc-400">
        已有账号？
        <router-link to="/login" class="text-primary-500 hover:text-primary-600 font-medium">返回登录</router-link>
      </div>
    </div>
  </div>
</template>
