import http from '@/api/http'
import type {
  ApiResponse,
  EnvironmentCycleResult,
  EnvironmentSnapshot,
  EnvironmentThreshold,
  EnvironmentTrend,
  TelemetryHistory,
  UpdateEnvironmentThresholdRequest,
} from '@/types/api'

export async function getLatestEnvironment(greenhouseId = 'GH-001') {
  const response = await http.get<ApiResponse<EnvironmentSnapshot>>('/environment/latest', {
    params: { greenhouseId },
  })
  return response.data.data
}

export async function getEnvironmentTrend(
  greenhouseCode = 'GH-001',
  limit = 24,
) {
  const response = await http.get<ApiResponse<EnvironmentTrend>>(
    '/environment/trend',
    {
      params: {
        greenhouseCode,
        limit,
      },
    },
  )
  return response.data.data
}

export async function getTelemetryHistory(
  greenhouseCode = 'GH-001',
  minutes = 30,
  bucketSeconds = 20,
) {
  const response = await http.get<ApiResponse<TelemetryHistory>>(
    '/iot/telemetry/history',
    {
      params: {
        greenhouseCode,
        minutes,
        bucketSeconds,
      },
    },
  )
  return response.data.data
}

export async function attestEnvironmentCycle(greenhouseCode = 'GH-001') {
  const response = await http.post<ApiResponse<EnvironmentCycleResult>>(
    '/environment/cycles/attest',
    { greenhouseCode },
  )
  return response.data.data
}

export async function listEnvironmentThresholds(greenhouseCode = 'GH-001') {
  const response = await http.get<ApiResponse<EnvironmentThreshold[]>>(
    '/environment/thresholds',
    {
      params: { greenhouseCode },
    },
  )
  return response.data.data
}

export async function updateEnvironmentThreshold(
  metricCode: string,
  payload: UpdateEnvironmentThresholdRequest,
) {
  const response = await http.put<ApiResponse<EnvironmentThreshold>>(
    `/environment/thresholds/${metricCode}`,
    payload,
  )
  return response.data.data
}
