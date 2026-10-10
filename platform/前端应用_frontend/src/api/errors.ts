/**
 * 把接口异常压成一句能直接给用户看的中文说明。
 * 后端统一返回 { code, message }，能拿到 message 就优先用它。
 */
export function describeApiError(error: unknown): string {
  const candidate = error as {
    message?: string
    response?: { status?: number; data?: { message?: string } }
  }
  const status = candidate?.response?.status
  const serverMessage = candidate?.response?.data?.message
  if (serverMessage) {
    return status ? `HTTP ${status} ${serverMessage}` : serverMessage
  }
  if (status) {
    return `HTTP ${status}`
  }
  return candidate?.message ?? '未知错误'
}
