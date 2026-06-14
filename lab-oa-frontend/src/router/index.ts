import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/token'

// ===== Route Definitions =====

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '仪表盘', icon: 'Monitor' },
      },
      // ===== Document Routes =====
      {
        path: 'document/library',
        name: 'DocumentLibrary',
        component: () => import('@/views/document/Library.vue'),
        meta: { title: '文献库', icon: 'Document', perm: 'literature:view' },
      },
      {
        path: 'document/upload',
        name: 'DocumentUpload',
        component: () => import('@/views/document/Upload.vue'),
        meta: { title: '上传文献', icon: 'Upload', perm: 'literature:upload', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
      },
      {
        path: 'document/batch-upload',
        name: 'DocumentBatchUpload',
        component: () => import('@/views/document/BatchUpload.vue'),
        meta: { title: '批量上传', icon: 'UploadFilled', perm: 'literature:batchUpload', roles: ['ADMIN'] },
      },
      {
        path: 'document/recycle',
        name: 'DocumentRecycle',
        component: () => import('@/views/document/Recycle.vue'),
        meta: { title: '回收站', icon: 'Delete', perm: 'literature:recycle', roles: ['ADMIN', 'TEACHER'] },
      },
      {
        path: 'document/search',
        name: 'DocumentSearch',
        component: () => import('@/views/document/Search.vue'),
        meta: { title: '搜索文献', icon: 'Search' },
      },
      {
        path: 'document/detail/:id',
        name: 'DocumentDetail',
        component: () => import('@/views/document/Detail.vue'),
        meta: { title: '文献详情', hidden: true },
      },
      // ===== Knowledge / RAG Routes =====
      {
        path: 'knowledge/rag-chat',
        name: 'RagChat',
        component: () => import('@/views/knowledge/RagChat.vue'),
        meta: { title: 'RAG问答', icon: 'ChatLineSquare', perm: 'rag:stream', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
      },
      {
        path: 'knowledge/chat-history',
        name: 'ChatHistory',
        component: () => import('@/views/knowledge/ChatHistory.vue'),
        meta: { title: '对话历史', icon: 'Clock', perm: 'rag:session', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
      },
      {
        path: 'knowledge/knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('@/views/knowledge/KnowledgeBase.vue'),
        meta: { title: '知识储备', icon: 'EditPen', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
      },
      // ===== Admin Routes =====
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', icon: 'User', perm: 'user:list', roles: ['ADMIN'] },
      },
      {
        path: 'admin/roles',
        name: 'AdminRoles',
        component: () => import('@/views/admin/Roles.vue'),
        meta: { title: '角色管理', icon: 'Avatar', perm: 'user:role', roles: ['ADMIN'] },
      },
      {
        path: 'admin/permissions',
        name: 'AdminPermissions',
        component: () => import('@/views/admin/Permissions.vue'),
        meta: { title: '权限管理', icon: 'Lock', perm: 'user:perm', roles: ['ADMIN'] },
      },
      {
        path: 'admin/operation-log',
        name: 'AdminOperationLog',
        component: () => import('@/views/admin/OperationLog.vue'),
        meta: { title: '操作日志', icon: 'DocumentChecked', roles: ['ADMIN'] },
      },
      {
        path: 'admin/folders',
        name: 'AdminFolders',
        component: () => import('@/views/admin/Folders.vue'),
        meta: { title: '目录管理', icon: 'FolderOpened', perm: 'literature:folder', roles: ['ADMIN'] },
      },
      {
        path: 'admin/literature',
        name: 'AdminLiterature',
        component: () => import('@/views/admin/LiteratureManage.vue'),
        meta: { title: '文献管理', icon: 'Document', perm: 'literature:recycle', roles: ['ADMIN'] },
      },
      {
        path: 'admin/site-config',
        name: 'AdminSiteConfig',
        component: () => import('@/views/admin/SiteConfig.vue'),
        meta: { title: '站点配置', icon: 'Setting', perm: 'system:config', roles: ['ADMIN'] },
      },
      // ===== Other Routes =====
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/Index.vue'),
        meta: { title: '个人中心', icon: 'UserFilled', hidden: true },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/Index.vue'),
        meta: { title: '系统设置', icon: 'Setting', hidden: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/Register.vue'),
    meta: { public: true },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/login/ForgotPassword.vue'),
    meta: { public: true },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true },
  },
]

// ===== Router Instance =====

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// ===== Navigation Guard =====

router.beforeEach(async (to, _from, next) => {
  const token = getToken()

  // Public pages
  if (to.meta.public) {
    if (token && ['/login', '/register', '/forgot-password'].includes(to.path)) {
      return next('/dashboard')
    }
    return next()
  }

  // Protected pages - must have token
  if (!token) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  // Ensure user info loaded — only fetch once, tolerate network errors
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  if (!userStore.user) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      // 网络波动或后端短暂不可用时不清除登录态
      // 仅当 token 已被 interceptor 清除（真 401）时才跳转登录
      if (!getToken()) return next({ name: 'Login' })
    }
  }

  // Check role requirement
  const requiredRoles = to.meta.roles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRole = requiredRoles.some((r) => userStore.roles.some(ur => ur.toUpperCase() === r.toUpperCase()))
    if (!hasRole) return next('/403')
  }

  next()
})

export default router
