import http from '@/api/http'
import type { ApiResponse, ChainProof } from '@/types/api'

export async function getChainProof(batchId: number) {
  const response = await http.get<ApiResponse<ChainProof>>(`/proofs/${batchId}`)
  return response.data.data
}
