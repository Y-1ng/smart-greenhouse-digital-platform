import http from '@/api/http'
import type {
  ApiResponse,
  ChainCompensationReport,
  ChainStatus,
} from '@/types/api'

export async function getChainStatus() {
  const response = await http.get<ApiResponse<ChainStatus>>('/chain/status')
  return response.data.data
}

export async function triggerChainCompensation() {
  const response = await http.post<ApiResponse<ChainCompensationReport>>(
    '/chain/compensate',
  )
  return response.data.data
}
