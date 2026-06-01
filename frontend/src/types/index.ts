// 与后端 DTO / 实体一一对应的类型定义。
// 后端控制台接口统一 Result<T> 包裹,分页用 PageResult<T>。

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface PageQuery {
  current: number
  size: number
}

// ---------- 用户 ----------
export interface User {
  id: number
  username: string
  email?: string | null
  /** admin / user */
  role: string
  /** 1 启用 0 禁用 */
  status: number
  /** 总额度(点数) */
  quota: number
  /** 已用额度(点数) */
  usedQuota: number
  createTime?: string
  updateTime?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface LoginResponse {
  token: string
  user: User
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// ---------- API Key ----------
export interface ApiKey {
  id: number
  userId: number
  name: string
  /** sk- 开头明文 */
  apiKey: string
  status: number
  /** 独立额度上限,0 表示跟随用户 */
  quota: number
  usedQuota: number
  /** 过期时间,null 永不过期 */
  expireTime?: string | null
  createTime?: string
  updateTime?: string
}

export interface CreateApiKeyRequest {
  name: string
  quota?: number
  expireTime?: string | null
}

// ---------- 渠道 ----------
export interface Channel {
  id: number
  name: string
  /** openai / azure / anthropic ... */
  type: string
  baseUrl: string
  /** 逗号分隔 */
  models: string
  /** 模型重命名映射(JSON) */
  modelMapping?: string | null
  status: number
  weight: number
  priority: number
  createTime?: string
  updateTime?: string
  // 注意:上游 apiKey 后端 @JsonIgnore,响应中不返回
}

export interface ChannelRequest {
  name: string
  type: string
  baseUrl: string
  /** 上游密钥(只写) */
  apiKey: string
  models: string
  modelMapping?: string
  weight: number
  priority: number
  status: number
}

// ---------- 模型 ----------
export interface Model {
  id: number
  modelName: string
  displayName?: string | null
  /** chat / embedding / image */
  type: string
  /** 输入每 token 点数 */
  promptPrice: number
  /** 输出每 token 点数 */
  completionPrice: number
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

export interface ModelRequest {
  modelName: string
  displayName?: string
  type: string
  promptPrice: number
  completionPrice: number
  status: number
  remark?: string
}

// ---------- 日志 ----------
export interface LogItem {
  id: number
  userId: number
  username: string
  apiKeyId: number
  channelId: number
  channelName: string
  modelName: string
  /** 1 成功 2 失败 */
  type: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  quota: number
  durationMs: number
  requestId?: string
  ip?: string
  content?: string
  createTime?: string
}

export interface LogStat {
  requests: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  quota: number
}
