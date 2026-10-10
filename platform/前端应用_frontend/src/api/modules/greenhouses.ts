import http from '@/api/http'
import type {
  ApiResponse,
  CreateGreenhouseRequest,
  GreenhouseOverview,
  GreenhouseSummary,
  UpdateGreenhouseCropRequest,
} from '@/types/api'

export async function listGreenhouses() {
  const response = await http.get<ApiResponse<GreenhouseSummary[]>>('/greenhouses')
  return response.data.data
}

export async function getGreenhouseOverview(greenhouseCode: string) {
  const response = await http.get<ApiResponse<GreenhouseOverview>>(
    `/greenhouses/${encodeURIComponent(greenhouseCode)}`,
  )
  return response.data.data
}

export async function createGreenhouse(payload: CreateGreenhouseRequest) {
  const response = await http.post<ApiResponse<GreenhouseSummary>>(
    '/greenhouses',
    payload,
  )
  return response.data.data
}

export async function updateGreenhouseCrop(
  greenhouseCode: string,
  payload: UpdateGreenhouseCropRequest,
) {
  const response = await http.put<ApiResponse<GreenhouseSummary>>(
    `/greenhouses/${encodeURIComponent(greenhouseCode)}/crop`,
    payload,
  )
  return response.data.data
}

export async function deleteGreenhouse(greenhouseCode: string) {
  await http.delete(`/greenhouses/${encodeURIComponent(greenhouseCode)}`)
}
