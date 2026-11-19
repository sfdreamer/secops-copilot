/** 后端统一响应包装，对应 API.md 1.2 / 1.3。 */
export interface ApiErrorBody {
  code: string
  details: unknown
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  error?: ApiErrorBody | null
}

/** 分页结构，对应 API.md 1.1 / 各列表接口。 */
export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  items: T[]
}

export interface HealthDto {
  status: string
  modelConfigured: boolean
  mockDataLoaded: boolean
}
