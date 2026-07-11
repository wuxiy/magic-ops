export interface DiagnosisSession {
  id: string
  targetApp: string
  targetHost: string
  targetPort: number
  status: 'creating' | 'attached' | 'detached' | 'closed' | 'error'
  createdAt: string
  updatedAt: string
  errorMessage?: string
}

export interface CommandTemplate {
  id: string
  name: string
  description: string
  commandPattern: string
  riskLevel: 'safe' | 'caution' | 'dangerous'
  category: string
  parameters: TemplateParameter[]
}

export interface TemplateParameter {
  name: string
  label: string
  type: 'string' | 'number' | 'boolean' | 'select'
  required: boolean
  defaultValue?: string | number | boolean
  options?: { label: string; value: string }[]
  placeholder?: string
}

export interface CommandExecution {
  id: string
  sessionId: string
  command: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'
  submittedAt: string
  completedAt?: string
  exitCode?: number
  outputPreview?: string
}

export interface SessionCreateRequest {
  targetApp: string
  targetHost: string
  targetPort: number
}
