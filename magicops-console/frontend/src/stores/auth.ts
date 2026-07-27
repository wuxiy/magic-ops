import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'
import type { User } from '@/types/api'

interface LoginResult {
  username: string
  authorities: string[]
  message: string
}

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<User | null>(null)
  const authenticated = ref(false)

  const isAuthenticated = computed(() => authenticated.value)
  const permissions = computed(() => currentUser.value?.permissions ?? [])

  async function login(username: string, password: string) {
    const { data } = await apiClient.post<LoginResult>('/login', { username, password })
    authenticated.value = true
    // Build a minimal User object from login response
    currentUser.value = {
      id: 0,
      username: data.username,
      displayName: data.username,
      role: (data.authorities || []).find(a => a.startsWith('ROLE_'))?.replace('ROLE_', '') ?? '',
      enabled: true,
      permissions: (data.authorities || []).filter(a => !a.startsWith('ROLE_')),
      createdAt: '',
      updatedAt: '',
    }
  }

  async function fetchCurrentUser() {
    try {
      const { data } = await apiClient.get<User>('/me')
      currentUser.value = data
      authenticated.value = true
    } catch {
      authenticated.value = false
      currentUser.value = null
    }
  }

  async function logout() {
    try {
      await apiClient.post('/logout')
    } finally {
      authenticated.value = false
      currentUser.value = null
    }
  }

  // Check session on store init; expose promise for router guard
  const sessionReady = fetchCurrentUser()

  return {
    currentUser,
    isAuthenticated,
    permissions,
    sessionReady,
    login,
    logout,
    fetchCurrentUser,
  }
})
