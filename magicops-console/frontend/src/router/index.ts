import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/views/LayoutView.vue'),
    meta: { requiresAuth: true },
    redirect: '/approval',
    children: [
      {
        path: 'approval',
        name: 'Approval',
        component: () => import('@/views/approval/ApprovalListView.vue'),
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/AuditListView.vue'),
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/user/UserListView.vue'),
      },
      {
        path: 'keys',
        name: 'Keys',
        component: () => import('@/views/key/KeyListView.vue'),
      },
      {
        path: 'publish',
        name: 'Publish',
        component: () => import('@/views/publish/PublishHistoryView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory('/console/'),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore()
  // Wait for initial session check to complete
  await auth.sessionReady
  if (to.meta.requiresAuth !== false && !auth.isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
