import http from '@/api/http'
import type {
  ApiResponse,
  ExternalLog,
  ExternalPlatformStatus,
  ExternalSyncReport,
} from '@/types/api'

export async function getExternalStatus() {
  const response = await http.get<ApiResponse<ExternalPlatformStatus[]>>(
    '/external/status',
  )
  return response.data.data
}

export async function syncExternalPlatform(platform = 'ALL') {
  const response = await http.post<ApiResponse<ExternalSyncReport[]>>(
    '/external/sync',
    null,
    { params: { platform } },
  )
  return response.data.data
}

export async function listExternalLogs(limit = 5) {
  const response = await http.get<ApiResponse<ExternalLog[]>>(
    '/external/logs',
    { params: { limit } },
  )
  return response.data.data
}
