import http from '@/api/http'
import type { ApiResponse, GovernmentDashboard } from '@/types/api'

export async function getGovernmentDashboard() {
  const response = await http.get<ApiResponse<GovernmentDashboard>>('/dashboard/government')
  return response.data.data
}

