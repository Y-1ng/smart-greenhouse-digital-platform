import { defineStore } from 'pinia'
import type { AuthUser, LoginResult } from '@/types/api'

const TOKEN_KEY = 'smart-greenhouse-token'
const USER_KEY = 'smart-greenhouse-user'

function readUser(): AuthUser | null {
  const raw = window.localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    window.localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    accessToken: window.localStorage.getItem(TOKEN_KEY) ?? '',
    user: readUser(),
  }),
  getters: {
    authenticated: (state) => Boolean(state.accessToken && state.user),
    displayName: (state) => state.user?.displayName ?? '未登录',
    roleCode: (state) => state.user?.roleCode ?? '',
  },
  actions: {
    setSession(result: LoginResult) {
      this.accessToken = result.accessToken
      this.user = result.user
      window.localStorage.setItem(TOKEN_KEY, result.accessToken)
      window.localStorage.setItem(USER_KEY, JSON.stringify(result.user))
    },
    logout() {
      this.accessToken = ''
      this.user = null
      window.localStorage.removeItem(TOKEN_KEY)
      window.localStorage.removeItem(USER_KEY)
    },
  },
})
