import http from '@/api/http'
import type { ApiResponse, LoginResult } from '@/types/api'

export interface LoginPayload {
  username: string
  password: string
}

export async function login(payload: LoginPayload) {
  const response = await http.post<ApiResponse<LoginResult>>(
    '/auth/login',
    payload,
  )
  return response.data.data
}
