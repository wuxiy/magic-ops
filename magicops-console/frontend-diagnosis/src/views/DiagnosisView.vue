<template>
  <div class="diagnosis-layout">
    <!-- 左侧面板：会话列表 -->
    <aside class="session-panel">
      <div class="panel-header">
        <h3>诊断会话</h3>
        <el-button type="primary" size="small" @click="showCreateDialog = true">
          新建会话
        </el-button>
      </div>
      <el-table
        :data="sessions"
        highlight-current-row
        size="small"
        class="session-table"
        @current-change="onSessionSelect"
      >
        <el-table-column prop="targetApp" label="应用" min-width="100" />
        <el-table-column prop="targetHost" label="主机" min-width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </aside>

    <!-- 右侧主区域 -->
    <main class="main-area">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <TemplatePicker
          v-model="selectedTemplate"
          class="template-picker"
        />
        <CommandForm
          v-if="selectedTemplate"
          :template="selectedTemplate"
          v-model:params="commandParams"
          class="command-form"
        />
        <el-button
          type="success"
          :disabled="!canExecute"
          :loading="executing"
          @click="executeCommand"
        >
          执行命令
        </el-button>
      </div>

      <!-- 终端区域 -->
      <div class="terminal-container">
        <ArthasTerminal
          ref="terminalRef"
          :session-id="activeSessionId"
        />
      </div>

      <!-- 底部状态栏 -->
      <div class="status-bar">
        <span>
          会话：{{ activeSession?.targetApp || '未选择' }}
          <template v-if="activeSession">
            ({{ activeSession.targetHost }}:{{ activeSession.targetPort }})
          </template>
        </span>
        <span>
          连接状态：
          <el-tag :type="wsStatusType" size="small">{{ wsStatusLabel }}</el-tag>
        </span>
      </div>
    </main>

    <!-- 新建会话对话框 -->
    <SessionCreateDialog
      v-model:visible="showCreateDialog"
      @created="onSessionCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import apiClient from '@/api/client'
import type { DiagnosisSession, CommandTemplate } from '@/types/diagnosis'
import ArthasTerminal from '@/components/ArthasTerminal.vue'
import SessionCreateDialog from '@/components/SessionCreateDialog.vue'
import TemplatePicker from '@/components/TemplatePicker.vue'
import CommandForm from '@/components/CommandForm.vue'

const sessions = ref<DiagnosisSession[]>([])
const activeSession = ref<DiagnosisSession | null>(null)
const showCreateDialog = ref(false)
const selectedTemplate = ref<CommandTemplate | null>(null)
const commandParams = ref<Record<string, unknown>>({})
const executing = ref(false)
const terminalRef = ref<InstanceType<typeof ArthasTerminal> | null>(null)

const activeSessionId = computed(() => activeSession.value?.id ?? null)

const canExecute = computed(() => {
  return activeSession.value && selectedTemplate.value && !executing.value
})

const wsStatusType = computed(() => {
  const s = terminalRef.value?.wsStatus
  if (s === 'connected') return 'success'
  if (s === 'connecting') return 'warning'
  return 'info'
})

const wsStatusLabel = computed(() => {
  const s = terminalRef.value?.wsStatus
  if (s === 'connected') return '已连接'
  if (s === 'connecting') return '连接中'
  return '未连接'
})

function statusTagType(status: string) {
  const map: Record<string, string> = {
    attached: 'success',
    creating: 'warning',
    detached: 'info',
    closed: 'info',
    error: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    creating: '创建中',
    attached: '已连接',
    detached: '已断开',
    closed: '已关闭',
    error: '异常',
  }
  return map[status] || status
}

async function loadSessions() {
  try {
    const { data } = await apiClient.get('/diagnosis/sessions')
    sessions.value = data
  } catch {
    ElMessage.error('加载会话列表失败')
  }
}

function onSessionSelect(row: DiagnosisSession | null) {
  activeSession.value = row
  if (row) {
    terminalRef.value?.connectToSession(row.id)
  }
}

function onSessionCreated(session: DiagnosisSession) {
  sessions.value.unshift(session)
  activeSession.value = session
  showCreateDialog.value = false
  terminalRef.value?.connectToSession(session.id)
}

async function executeCommand() {
  if (!activeSession.value || !selectedTemplate.value) return
  executing.value = true
  try {
    const filledCommand = buildCommand(selectedTemplate.value, commandParams.value)
    await apiClient.post(`/diagnosis/sessions/${activeSession.value.id}/commands`, {
      command: filledCommand,
    })
    ElMessage.success('命令已提交')
  } catch {
    ElMessage.error('命令执行失败')
  } finally {
    executing.value = false
  }
}

function buildCommand(template: CommandTemplate, params: Record<string, unknown>): string {
  let cmd = template.commandPattern
  for (const [key, value] of Object.entries(params)) {
    cmd = cmd.replace(`{{${key}}}`, String(value ?? ''))
  }
  return cmd
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.diagnosis-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.session-panel {
  width: 320px;
  min-width: 280px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
}

.session-table {
  flex: 1;
  overflow: auto;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
  background: #fff;
}

.template-picker {
  min-width: 200px;
}

.command-form {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.terminal-container {
  flex: 1;
  overflow: hidden;
  background: #000;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 16px;
  font-size: 12px;
  color: #606266;
  border-top: 1px solid #e4e7ed;
  background: #f5f7fa;
}
</style>
