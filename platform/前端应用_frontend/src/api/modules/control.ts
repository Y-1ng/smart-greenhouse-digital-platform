import http from '@/api/http'
import type {
  ApiResponse,
  ControlCommandAckRequest,
  ControlCommandRequest,
  ControlCommandResult,
} from '@/types/api'

export async function createControlCommand(payload: ControlCommandRequest) {
  const response = await http.post<ApiResponse<ControlCommandResult>>('/control/commands', payload)
  return response.data.data
}

export async function acknowledgeControlCommand(
  commandNo: string,
  payload: ControlCommandAckRequest,
) {
  const response = await http.post<ApiResponse<ControlCommandResult>>(
    `/control/commands/${encodeURIComponent(commandNo)}/ack`,
    payload,
  )
  return response.data.data
}

export async function getControlCommand(commandNo: string) {
  const response = await http.get<ApiResponse<ControlCommandResult>>(
    `/control/commands/${encodeURIComponent(commandNo)}`,
  )
  return response.data.data
}
