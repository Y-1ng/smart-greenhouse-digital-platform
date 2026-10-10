import http from '@/api/http'
import type {
  ApiResponse,
  CreateWaterFertilizerPlanRequest,
  WaterFertilizerPlan,
} from '@/types/api'

export async function listWaterFertilizerPlans(
  greenhouseCode: string,
  date: string,
) {
  const response = await http.get<ApiResponse<WaterFertilizerPlan[]>>(
    '/water-fertilizer/plans',
    {
      params: {
        greenhouseCode,
        date,
      },
    },
  )
  return response.data.data
}

export async function createWaterFertilizerPlan(
  payload: CreateWaterFertilizerPlanRequest,
) {
  const response = await http.post<ApiResponse<WaterFertilizerPlan>>(
    '/water-fertilizer/plans',
    payload,
  )
  return response.data.data
}

export async function executeWaterFertilizerPlan(
  planNo: string,
  operatorName: string,
) {
  const response = await http.post<ApiResponse<WaterFertilizerPlan>>(
    `/water-fertilizer/plans/${encodeURIComponent(planNo)}/execute`,
    {
      operatorName,
      executedAt: new Date().toISOString().slice(0, 19),
    },
  )
  return response.data.data
}
