/** Generic paginated response from Spring Data */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

/** Login response */
export interface LoginResponse {
  token: string
  expiresIn: number
}

/** 当前登录用户(来自 /api/login 与 /api/me 的 authorities) */
export interface CurrentUser {
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

/** 用户管理列表项(对应后端 UserResponse) */
export interface ManagedUser {
  id: number
  username: string
  displayName: string | null
  email: string | null
  enabled: boolean
  locked: boolean
  createdAt: string
}

/** 角色(对应后端 RoleEntity) */
export interface Role {
  id: number
  name: string
  displayName: string | null
  description: string | null
}

/** Approval record */
export interface Approval {
  id: number
  entityType: string
  entityId: string
  action: string
  requestedBy: string
  decision: 'PENDING' | 'APPROVED' | 'REJECTED'
  decidedBy: string | null
  comment: string | null
  createdAt: string
  decidedAt: string | null
}

/** Audit log record */
export interface AuditRecord {
  id: number
  traceId: string
  entityType: string
  entityId: string
  eventType: string
  operator: string
  eventTimestamp: string
  critical: boolean
  details: string
}

/** Key/certificate entry */
export interface KeyEntry {
  id: number
  name: string
  keyType: string
  status: 'ACTIVE' | 'REVOKED'
  fingerprint: string
  createdAt: string
  expiresAt: string | null
}

/** Publish/deploy record */
export interface PublishRecord {
  id: number
  packageName: string
  version: string
  environment: string
  status: 'SUCCESS' | 'FAILED' | 'ROLLBACK'
  publishedBy: string
  publishedAt: string
  approvalId: number | null
  note: string | null
}
