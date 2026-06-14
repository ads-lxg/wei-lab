import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { getToken, removeToken } from '@/utils/token'
import type { Result } from '@/types'
import router from '@/router'
import { useUserStore } from '@/stores/user'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// Request interceptor - attach SaToken (raw token, no Bearer prefix)
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token && config.headers) {
      // SaToken token-name=Authorization 默认无前缀，直接传原始 token
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor - unwrap Result, handle errors
instance.interceptors.response.use(
  (response) => {
    const result = response.data as Result
    if (result.code !== 200 && result.code !== 0) {
      ElMessage.error(result.message || '请求失败')
      return Promise.reject(new Error(result.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      removeToken()
      useUserStore().logout()
      router.replace({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
      ElMessage.error('登录已过期，请重新登录')
    } else if (error.response?.status === 403) {
      // 403 静默跳转，不弹 alert
      router.replace({ name: 'Forbidden' })
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

// Typed request methods that unwrap Result.data
export async function get<T>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<T> {
  const res = await instance.get<Result<T>>(url, { ...config, params })
  return res.data.data
}

export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await instance.post<Result<T>>(url, data, config)
  return res.data.data
}

export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await instance.put<Result<T>>(url, data, config)
  return res.data.data
}

export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await instance.delete<Result<T>>(url, config)
  return res.data.data
}

export function postForm<T>(url: string, formData: FormData): Promise<T> {
  return post<T>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000, // 2min for uploads
  })
}

export function postStream(
  url: string,
  data: unknown,
  onDelta: (text: string) => void,
  onDone: (meta: Record<string, unknown>) => void,
  onError: (err: string) => void,
): AbortController {
  const token = getToken()
  const controller = new AbortController()

  fetch(`/api${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      if (response.status === 401) {
        removeToken()
        router.replace({ name: 'Login' })
      }
      onError(`HTTP ${response.status}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) { onError('No response body'); return }
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''
    let dataLines: string[] = []

    /** 派发当前累积的 SSE 事件 */
    function flushEvent() {
      if (dataLines.length === 0) return
      const dataStr = dataLines.join('\n')
      dataLines = []
      if (currentEvent === 'delta') {
        onDelta(dataStr)
      } else if (currentEvent === 'done') {
        try { onDone(JSON.parse(dataStr)) } catch { onDone({}) }
      } else if (currentEvent === 'error') {
        onError(dataStr)
      } else if (currentEvent === 'info') {
        /* 静默 */
      } else if (dataStr.trim().startsWith('{') && dataStr.includes('"citations"')) {
        try { onDone(JSON.parse(dataStr)) } catch { /* ignore */ }
      }
      currentEvent = ''
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 统一处理 \r\n 和 \n 行尾
      const lines = buffer.replace(/\r\n/g, '\n').split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        // 新的 event: 行到来时，先派发之前累积的事件
        if (line.startsWith('event:')) {
          flushEvent()
          currentEvent = line.slice(6).trim()
          continue
        }
        // 空行 = SSE 事件结束
        if (line === '') {
          flushEvent()
          continue
        }
        if (line.startsWith('data:')) {
          // SSE 规范：data: 后可选一个空格，需去除
          let val = line.slice(5)
          if (val.startsWith(' ')) val = val.slice(1)
          dataLines.push(val)
          continue
        }
        // 忽略 id:, retry:, 注释行等
      }
    }
    // 处理流结束时可能残留的事件
    flushEvent()
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError(err.message)
    }
  })

  return controller
}

export default instance
