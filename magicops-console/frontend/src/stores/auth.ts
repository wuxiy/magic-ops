import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import apiClient from '@/api/client'
import type { User, LoginResponse } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<User | null>(null)
  const token = ref<string>(localStorage.getItem('magicops_token') ?? '')

  const isAuthenticated = computed(() => !!token.value)
  const permissions = computed(() => currentUser.value?.permissions ?? [])

  async function login(username: string, password: string) {
    const { data } = await apiClient.post<LoginResponse>('/login', { username, password })
    token.value = data.token
    localStorage.setItem('magicops_token', data.token)
    await fetchCurrentUser()
  }

  async function fetchCurrentUser() {
    try {
      const { data } = await apiClient.get<User>('/me')
      currentUser.value = data
    } catch {
      logout()
    }
  }

  async function logout() {
    try {
      await apiClient.post('/logout')
    } finally {
      token.value = ''
      currentUser.value = null
      localStorage.removeItem('magicops_token')
    }
  }

  // Restore session on store init
  if (token.value) {
    fetchCurrentUser()
  }

  return {
    currentUser,
    token,
    isAuthenticated,
    permissions,
    login,
    logout,
    fetchCurrentUser,
  }
})
