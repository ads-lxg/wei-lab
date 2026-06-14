<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useConfigStore } from '@/stores/config'
import { listConfigs, updateConfigs } from '@/api/config'

const configStore = useConfigStore()
const siteName = ref('')
const siteIconBase64 = ref('')
const saving = ref(false)
const uploading = ref(false)
// 是否有已保存的图标（用于区分"从未上传"和"已上传但当前预览是本地base64"）
const hasExistingIcon = ref(false)

// 图标预览：优先用本地 base64（刚选的），否则用后端 API
const iconPreview = computed(() => {
  if (siteIconBase64.value) return siteIconBase64.value
  if (hasExistingIcon.value) return '/api/config/icon'
  return ''
})

onMounted(async () => {
  siteName.value = configStore.siteName
  // 检查是否已有图标（通过 site_icon_file_id 或 site_icon_base64）
  try {
    const configs = await listConfigs()
    hasExistingIcon.value = !!(configs.site_icon_file_id || configs.site_icon_base64)
  } catch {
    hasExistingIcon.value = false
  }
})

async function handleFileChange(file: any) {
  if (!file?.raw) return
  uploading.value = true
  try {
    const raw: File = file.raw
    // 限制大小 2MB
    if (raw.size > 2 * 1024 * 1024) {
      ElMessage.warning('图标文件不能超过 2MB')
      uploading.value = false
      return
    }
    // 读取为 base64 data URL（后端会解码后上传到 MinIO，不存 MySQL）
    const reader = new FileReader()
    reader.onload = (e) => {
      siteIconBase64.value = e.target?.result as string
      uploading.value = false
    }
    reader.onerror = () => {
      ElMessage.error('读取文件失败')
      uploading.value = false
    }
    reader.readAsDataURL(raw)
  } catch {
    ElMessage.error('图标处理失败')
    uploading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const configs: Record<string, string> = { site_name: siteName.value }
    if (siteIconBase64.value) {
      // 传 base64 给后端，后端会解码上传到 MinIO，MySQL 只存 fileId
      configs.site_icon_base64 = siteIconBase64.value
    }
    await updateConfigs(configs)
    // 保存后刷新图标（加时间戳避免浏览器缓存）
    configStore.updateLocal({
      site_name: siteName.value,
      site_icon_file_id: 'updated',
    })
    hasExistingIcon.value = true
    siteIconBase64.value = '' // 清除本地预览，使用 API 端点
    document.title = siteName.value
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div>
      <h1 class="text-2xl font-bold" style="color: var(--text-primary)">站点配置</h1>
      <p class="text-sm mt-1" style="color: var(--text-muted)">自定义网站名称和图标</p>
    </div>

    <div class="card p-6 space-y-6">
      <!-- 网站名称 -->
      <div>
        <label class="text-sm font-medium block mb-2" style="color: var(--text-secondary)">网站名称</label>
        <el-input v-model="siteName" placeholder="输入网站名称" maxlength="50" show-word-limit />
        <p class="text-xs mt-1" style="color: var(--text-muted)">显示在侧边栏顶部和浏览器标签页</p>
      </div>

      <!-- 网站图标 -->
      <div>
        <label class="text-sm font-medium block mb-2" style="color: var(--text-secondary)">网站图标</label>
        <div class="flex items-start gap-4">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :on-change="handleFileChange"
          >
            <div
              class="w-20 h-20 rounded-xl border-2 border-dashed flex items-center justify-center cursor-pointer hover:border-primary-400 transition-colors"
              style="border-color: var(--border-color)"
            >
              <img v-if="iconPreview" :src="iconPreview" alt="Preview" class="w-16 h-16 rounded-lg object-cover" />
              <el-icon v-else :size="28" style="color: var(--text-muted)"><Plus /></el-icon>
            </div>
          </el-upload>
          <div class="flex-1">
            <p class="text-sm" style="color: var(--text-secondary)">
              点击左侧区域上传图标
            </p>
            <p class="text-xs mt-1" style="color: var(--text-muted)">
              支持 PNG、JPG、SVG 等格式，建议尺寸 64x64 以上，不超过 2MB。图标存储在对象存储中，永不过期。
            </p>
            <div v-if="uploading" class="mt-2">
              <el-tag type="warning" size="small">上传中...</el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 预览 -->
      <div>
        <label class="text-sm font-medium block mb-2" style="color: var(--text-secondary)">效果预览</label>
        <div class="flex items-center gap-3 p-4 rounded-xl border" style="border-color: var(--border-color); background: var(--bg-secondary)">
          <img v-if="iconPreview" :src="iconPreview" alt="Preview" class="w-7 h-7 rounded shrink-0" />
          <span class="text-base font-semibold" style="color: var(--text-primary)">{{ siteName || '网站名称' }}</span>
        </div>
      </div>

      <!-- 保存 -->
      <div class="flex justify-end pt-2">
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon :size="14"><Check /></el-icon> 保存配置
        </el-button>
      </div>
    </div>
  </div>
</template>
