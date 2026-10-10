import http from '@/api/http'
import type {
  ApiResponse,
  CooperationShare,
  CreateDividendRequest,
  CreateShareRequest,
  Dividend,
  VillageSummary,
} from '@/types/api'

export async function listVillages() {
  const response = await http.get<ApiResponse<VillageSummary[]>>('/cooperation/villages')
  return response.data.data
}

export async function createShare(payload: CreateShareRequest) {
  const response = await http.post<ApiResponse<CooperationShare>>(
    '/cooperation/shares',
    payload,
  )
  return response.data.data
}

export async function createDividend(payload: CreateDividendRequest) {
  const response = await http.post<ApiResponse<Dividend>>(
    '/cooperation/dividends',
    payload,
  )
  return response.data.data
}

export async function deleteCooperationShare(shareId: number) {
  const response = await http.delete<ApiResponse<boolean>>(
    `/cooperation/shares/${shareId}`,
  )
  return response.data.data
}
