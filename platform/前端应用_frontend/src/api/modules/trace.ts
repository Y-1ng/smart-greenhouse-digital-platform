import http from '@/api/http'
import type {
  ApiResponse,
  CreateTraceCodeRequest,
  ScanResult,
  TraceCodeRecord,
  TraceQrPayload,
  TraceNodeResult,
  TracePath,
  TraceResult,
} from '@/types/api'

export async function queryTrace(productCode: string) {
  const response = await http.get<ApiResponse<TraceResult>>(`/trace/${encodeURIComponent(productCode)}`)
  return response.data.data
}

export async function createTraceCode(payload: CreateTraceCodeRequest) {
  const response = await http.post<ApiResponse<TraceCodeRecord>>(
    '/trace-codes',
    payload,
  )
  return response.data.data
}

/**
 * 取二维码载荷：二维码里只放公开校验地址，前端预览与后端出图共用同一个字符串。
 */
export async function getTraceQrPayload(productCode: string) {
  const response = await http.get<ApiResponse<TraceQrPayload>>(
    `/trace-codes/${encodeURIComponent(productCode)}/qr-payload`,
  )
  return response.data.data
}

export async function scanTraceCode(
  productCode: string,
  payload: { channel: string; deviceFingerprint: string },
) {
  const response = await http.post<ApiResponse<ScanResult>>(
    `/trace/codes/${encodeURIComponent(productCode)}/scan`,
    payload,
  )
  return response.data.data
}

export async function getFieldTracePath(fieldNo: string) {
  const response = await http.get<ApiResponse<TracePath>>(
    `/trace/field/${encodeURIComponent(fieldNo)}/path`,
  )
  return response.data.data
}

export async function appendTraceNode(
  fieldNo: string,
  payload: { stageType: string; summary?: string; payloadHash?: string },
) {
  const response = await http.post<ApiResponse<TraceNodeResult>>(
    `/trace/fields/${encodeURIComponent(fieldNo)}/nodes`,
    payload,
  )
  return response.data.data
}
