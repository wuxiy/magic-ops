import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<{ username: string; roles: string[] } | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!user.value)

  async function checkSession() {
    loading.value = true
    try {
      const { data } = await apiClient.get('/me')
      user.value = data
    } catch {
      user.value = null
    } finally {
      loading.value = false
    }
  }

  function logout() {
    user.value = null
  }

  return { user, loading, isAuthenticated, checkSession, logout }
})
