import { request } from './http'
import type { EvaluationDto } from '@/types/evaluation'

export function fetchEvaluation(): Promise<EvaluationDto> {
  return request<EvaluationDto>({ url: '/evaluation', method: 'GET' })
}
