import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'
import type { CurrentUser } from '@/types/api'

interface AuthResult {
  username: string
  authorities: string[]
}

function toCurrentUser(data: AuthResult): CurrentUser {
  const authorities = data.authorities || []
  return {
    username: data.username,
    displayName: data.username,
    roles: authorities.filter(a => a.startsWith('ROLE_')).map(a => a.replace('ROLE_', '')),
    permissions: authorities.filter(a => !a.startsWith('ROLE_')),
  }
}

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<CurrentUser | null>(null)
  const authenticated = ref(false)

  const isAuthenticated = computed(() => authenticated.value)
  const permissions = computed(() => currentUser.value?.permissions ?? [])

  async function login(username: string, password: string) {
    const { data } = await apiClient.post<AuthResult>('/login', { username, password })
    currentUser.value = toCurrentUser(data)
    authenticated.value = true
  }

  async function fetchCurrentUser() {
    try {
      const { data } = await apiClient.get<AuthResult>('/me', { skipErrorMessage: true })
      currentUser.value = toCurrentUser(data)
      authenticated.value = true
    } catch {
      authenticated.value = false
      currentUser.value = null
    }
  }

  async function logout() {
    try {
      await apiClient.post('/logout', null, { skipErrorMessage: true })
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
