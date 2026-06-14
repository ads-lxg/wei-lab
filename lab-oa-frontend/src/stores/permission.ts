import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Permission store - holds the user's permission codes and role codes
 * Populated after login via userStore.fetchUserInfo
 */
export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref<string[]>([])
  const roleCodes = ref<string[]>([])

  function setPermissions(perms: string[], roles: string[]) {
    permissions.value = perms
    roleCodes.value = roles
  }

  function hasPermission(permCode: string): boolean {
    if (roleCodes.value.includes('ADMIN')) return true
    return permissions.value.includes(permCode)
  }

  function hasAnyPermission(...codes: string[]): boolean {
    if (roleCodes.value.includes('ADMIN')) return true
    return codes.some((c) => permissions.value.includes(c))
  }

  function clear() {
    permissions.value = []
    roleCodes.value = []
  }

  return { permissions, roleCodes, setPermissions, hasPermission, hasAnyPermission, clear }
})
