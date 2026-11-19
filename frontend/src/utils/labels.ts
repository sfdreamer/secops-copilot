/** 枚举中文标签与颜色，仅用于展示；取值以 DATABASE.md 4 节为准。 */

export const ALERT_TYPE_LABEL: Record<string, string> = {
  SSH_BRUTE_FORCE: 'SSH 暴力破解',
  PORT_SCAN: '端口扫描',
  SQL_INJECTION: 'SQL 注入',
  MALWARE_ACTIVITY: '恶意软件活动',
  UNKNOWN: '未知类型',
}

export const CATEGORY_LABEL: Record<string, string> = {
  AUTHENTICATION: '身份认证',
  NETWORK: '网络攻击',
  WEB_ATTACK: 'Web 攻击',
  HOST_SECURITY: '主机安全',
  DATA_SECURITY: '数据安全',
  UNKNOWN: '未知',
}

export const SEVERITY_LABEL: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  INFO: '信息',
}

export const RISK_LABEL: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export const RISK_COLOR: Record<string, string> = {
  CRITICAL: '#ff5c74',
  HIGH: '#ff9f43',
  MEDIUM: '#f5d544',
  LOW: '#3ddc97',
}

export const SEVERITY_COLOR: Record<string, string> = {
  CRITICAL: '#ff5c74',
  HIGH: '#ff9f43',
  MEDIUM: '#f5d544',
  LOW: '#3ddc97',
  INFO: '#38c6f1',
}

export const INCIDENT_STATUS_LABEL: Record<string, string> = {
  PENDING_ANALYSIS: '待分析',
  ANALYZING: '研判中',
  PENDING_REVIEW: '待审核',
  ANALYSIS_FAILED: '分析失败',
  CONFIRMED_THREAT: '已确认威胁',
  FALSE_POSITIVE: '已标记误报',
  INVESTIGATING: '进一步调查',
}

export const INCIDENT_STATUS_COLOR: Record<string, string> = {
  PENDING_ANALYSIS: '#38c6f1',
  ANALYZING: '#6ee7ff',
  PENDING_REVIEW: '#f5b544',
  ANALYSIS_FAILED: '#ff5c74',
  CONFIRMED_THREAT: '#ff5c74',
  FALSE_POSITIVE: '#3ddc97',
  INVESTIGATING: '#9a8cff',
}

export const ANALYSIS_STATUS_LABEL: Record<string, string> = {
  NOT_STARTED: '未开始',
  RUNNING: '进行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
}

export const VERDICT_LABEL: Record<string, string> = {
  SUSPICIOUS: '疑似威胁',
  LIKELY_BENIGN: '疑似正常',
  NEEDS_REVIEW: '需要复核',
}

export const VERDICT_COLOR: Record<string, string> = {
  SUSPICIOUS: '#ff5c74',
  LIKELY_BENIGN: '#3ddc97',
  NEEDS_REVIEW: '#f5b544',
}

export const REVIEW_RESULT_LABEL: Record<string, string> = {
  CONFIRMED_THREAT: '确认威胁',
  FALSE_POSITIVE: '标记误报',
  INVESTIGATING: '进一步调查',
}

export const TOOL_NAME_LABEL: Record<string, string> = {
  query_login_history: '历史登录查询',
  query_authorized_operations: '授权运维查询',
}

export const EXECUTION_STATUS_LABEL: Record<string, string> = {
  SUCCESS: '调用成功',
  FAILED: '调用失败',
  TIMEOUT: '调用超时',
  REJECTED: '已被拒绝',
}

export const EXECUTION_STATUS_COLOR: Record<string, string> = {
  SUCCESS: '#3ddc97',
  FAILED: '#ff5c74',
  TIMEOUT: '#f5b544',
  REJECTED: '#8aa6c6',
}

export const VALIDATION_STATUS_LABEL: Record<string, string> = {
  VALID: '校验通过',
  PARTIALLY_VALID: '部分有效',
  INVALID: '校验失败',
}

export const EXECUTION_MODE_LABEL: Record<string, string> = {
  TOOL_ENABLED: '工具增强研判',
  NO_TOOL_FALLBACK: '无工具降级研判',
}

export function labelOf(map: Record<string, string>, value: string | null | undefined): string {
  if (!value) return '—'
  return map[value] ?? value
}

/** 将 HEX 颜色转成带透明度的 rgba，用于标签底色。 */
export function tint(hex: string, alpha: number): string {
  const n = hex.replace('#', '')
  const r = parseInt(n.slice(0, 2), 16)
  const g = parseInt(n.slice(2, 4), 16)
  const b = parseInt(n.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
