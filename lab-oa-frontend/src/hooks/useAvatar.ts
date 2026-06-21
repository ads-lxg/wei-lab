import { ref, watch, toRef, onUnmounted, type Ref, type MaybeRef } from 'vue'
import { getToken } from '@/utils/token'

/**
 * 将需要认证的后端头像路径转换为可在 <img> 标签中直接使用的 blob URL。
 *
 * 背景：后端头像接口 /api/file/{id}/stream 需要 Authorization 请求头，
 * 但原生 <img> 标签无法携带自定义请求头，导致 401 无法加载。
 * 本 composable 通过 fetch（带 Auth 头）获取图片，转为 blob URL 解决此问题。
 */
export function useAvatarUrl(avatarPath: MaybeRef<string | undefined | null>): Ref<string> {
  const blobUrl = ref('')
  const pathRef = toRef(avatarPath)
  let objectUrl: string | null = null

  async function load(path: string) {
    try {
      const token = getToken()
      const res = await fetch(path, {
        headers: token ? { Authorization: token } : {},
      })
      if (res.ok) {
        const blob = await res.blob()
        // 释放旧的 object URL
        if (objectUrl) URL.revokeObjectURL(objectUrl)
        objectUrl = URL.createObjectURL(blob)
        blobUrl.value = objectUrl
        return
      }
    } catch {
      // 网络错误或跨域
    }
    blobUrl.value = ''
  }

  watch(pathRef, (newPath) => {
    if (newPath) {
      load(newPath)
    } else {
      blobUrl.value = ''
    }
  }, { immediate: true })

  onUnmounted(() => {
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  })

  return blobUrl
}

/**
 * 批量将后端头像路径转为 blob URL（适用于用户列表等场景）。
 * 返回一个 Map: 原始路径 → blobUrl
 */
export async function batchFetchAvatarUrls(paths: string[]): Promise<Map<string, string>> {
  const result = new Map<string, string>()
  const token = getToken()

  const tasks = paths
    .filter((p) => p && !result.has(p))
    .map(async (path) => {
      try {
        const res = await fetch(path, {
          headers: token ? { Authorization: token } : {},
        })
        if (res.ok) {
          const blob = await res.blob()
          result.set(path, URL.createObjectURL(blob))
        }
      } catch {
        // ignore
      }
    })

  await Promise.all(tasks)
  return result
}
