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

/** User entity */
export interface User {
  id: number
  username: string
  displayName: string
  role: string
  enabled: boolean
  permissions: string[]
  createdAt: string
  updatedAt: string
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
  entityType: string
  entityId: string
  eventType: string
  operator: string
  timestamp: string
  detail: string
  ipAddress: string
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
