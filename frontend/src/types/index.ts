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
  /** 分组,决定可路由到哪些渠道 */
  group: string
  /** 模型白名单(逗号分隔),空表示不限制 */
  models?: string | null
  /** 过期时间,null 永不过期 */
  expireTime?: string | null
  createTime?: string
  updateTime?: string
}

export interface CreateApiKeyRequest {
  name: string
  group?: string
  models?: string
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
  /** 分组,逗号分隔,可属多组 */
  group: string
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
  /** 上游密钥(只写,支持换行分隔多 key)。编辑时留空表示沿用库中原密钥 */
  apiKey: string
  models: string
  /** 分组,逗号分隔,可属多组 */
  group: string
  modelMapping?: string
  weight: number
  priority: number
  status: number
}

/** 拉取上游模型列表入参 */
export interface FetchModelsRequest {
  baseUrl: string
  /** 留空且带 id 时,后端用库中该渠道的原密钥 */
  apiKey?: string
  id?: number | null
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
}
