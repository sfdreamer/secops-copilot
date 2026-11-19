import axios from 'axios'
import type { AxiosError, AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'

/** 前端只调用公共 /api 接口，不直接请求 DashScope 或内部工具（ARCHITECTURE.md 7）。 */
const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const message =
      error.response?.data?.message ?? error.message ?? '请求失败，请稍后重试'
    return Promise.reject(new Error(message))
  },
)

/** 拆包统一响应；业务失败统一转为 Error，供页面展示 Error 状态。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  const payload = response.data
  if (!payload.success) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

export default http
