import http from '@/api/http'
import type {
  ApiResponse,
  CertificateRecord,
  CertificateVerifyRequest,
  CertificateVerifyResult,
  CropBatch,
  CreateCertificateRequest,
  QualityReport,
  QualityReportUploadRequest,
} from '@/types/api'

export async function listCropBatches() {
  const response = await http.get<ApiResponse<CropBatch[]>>('/crop-batches')
  return response.data.data
}

export async function uploadQualityReport(payload: QualityReportUploadRequest) {
  const formData = new FormData()
  formData.append('batchId', String(payload.batchId))
  formData.append('institutionId', payload.institutionId)
  formData.append('institutionName', payload.institutionName)
  formData.append('reportNo', payload.reportNo)
  formData.append('resultSummary', payload.resultSummary)
  formData.append('testedAt', payload.testedAt)
  formData.append('file', payload.file)

  const response = await http.post<ApiResponse<QualityReport>>(
    '/quality/reports',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  )
  return response.data.data
}

export async function getQualityReport(reportNo: string) {
  const response = await http.get<ApiResponse<QualityReport>>(
    `/quality/reports/${encodeURIComponent(reportNo)}`,
  )
  return response.data.data
}

export async function downloadQualityReport(reportNo: string) {
  const response = await http.get<Blob>(
    `/quality/reports/${encodeURIComponent(reportNo)}/file`,
    {
      responseType: 'blob',
    },
  )
  return response.data
}

export async function createCertificate(payload: CreateCertificateRequest) {
  const response = await http.post<ApiResponse<CertificateRecord>>(
    '/certificates',
    payload,
  )
  return response.data.data
}

export async function verifyCertificateSignature(
  payload: CertificateVerifyRequest,
) {
  const response = await http.post<ApiResponse<CertificateVerifyResult>>(
    '/certificates/verify',
    payload,
  )
  return response.data.data
}
