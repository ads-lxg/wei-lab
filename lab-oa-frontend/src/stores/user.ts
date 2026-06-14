import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/token'
import { login as loginApi, getUserInfo, register as registerApi } from '@/api/auth'
import type { LoginDTO, RegisterDTO, UserVO } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const user = ref<UserVO | null>(null)
  const roles = ref<string[]>([])

  const isLoggedIn = () => !!token.value

  async function login(data: LoginDTO) {
    const res = await loginApi(data)
    token.value = res.token
    user.value = res.user
    roles.value = res.user.roles || []
    setToken(res.token)
  }

  async function fetchUserInfo() {
    const info = await getUserInfo()
    user.value = info
    roles.value = info.roles || []
  }

  async function doRegister(data: RegisterDTO) {
    await registerApi(data)
  }

  function logout() {
    token.value = null
    user.value = null
    roles.value = []
    removeToken()
  }

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  return { token, user, roles, isLoggedIn, login, fetchUserInfo, doRegister, logout, hasRole }
}, {
  persist: true,
})
