import { createRouter, createWebHistory } from 'vue-router'
import DiagnosisView from '@/views/DiagnosisView.vue'

const router = createRouter({
  history: createWebHistory('/diagnosis/'),
  routes: [
    {
      path: '/',
      name: 'diagnosis',
      component: DiagnosisView,
    },
  ],
})

export default router
