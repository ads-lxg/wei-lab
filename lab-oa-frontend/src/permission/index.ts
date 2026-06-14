import type { Router, RouteRecordRaw } from 'vue-router'

/**
 * Filter routes based on user roles
 * Routes without meta.roles are public (accessible to all)
 * Routes with meta.roles require at least one matching role
 */
export function filterAsyncRoutes(routes: RouteRecordRaw[], roles: string[]): RouteRecordRaw[] {
  const filtered: RouteRecordRaw[] = []

  for (const route of routes) {
    const tmp = { ...route }
    const requiredRoles = (tmp.meta as Record<string, unknown> | undefined)?.['roles'] as string[] | undefined

    if (requiredRoles && requiredRoles.length > 0) {
      const hasRole = requiredRoles.some((r) => roles.includes(r))
      if (!hasRole) continue
    }

    if (tmp.children && tmp.children.length > 0) {
      tmp.children = filterAsyncRoutes(tmp.children, roles)
      if (tmp.children.length === 0) continue
    }

    filtered.push(tmp)
  }

  return filtered
}

/**
 * Check if a permission code is available
 */
export function checkPerm(permCode: string, permissions: string[], roles: string[]): boolean {
  if (roles.includes('ADMIN')) return true
  return permissions.includes(permCode)
}
