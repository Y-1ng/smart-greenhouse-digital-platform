import http from '@/api/http'
import type { AlertRecord, ApiResponse } from '@/types/api'

export async function listAlerts(
  params: {
    greenhouseCode?: string
    status?: string
    limit?: number
  } = {},
) {
  const response = await http.get<ApiResponse<AlertRecord[]>>('/alerts', {
    params,
  })
  return response.data.data
}

export async function acknowledgeAlert(alertId: number, operator: string) {
  const response = await http.post<ApiResponse<AlertRecord>>(
    `/alerts/${alertId}/ack`,
    { operator },
  )
  return response.data.data
}
