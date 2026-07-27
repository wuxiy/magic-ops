<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>密钥管理</span>
          <el-button type="primary" :loading="rotating" @click="rotateKey">
            <el-icon><Refresh /></el-icon>
            轮换密钥
          </el-button>
        </div>
      </template>

      <el-alert
        title="密钥用于脚本签名与验证。轮换会生成新的活跃密钥，原活跃密钥转为停用；停用密钥仍可用于验证历史签名。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-table :data="keyRows" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="keyId" label="密钥 ID" min-width="280" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DEPRECATED'"
              size="small"
              type="danger"
              @click="removeKey(row)"
            >删除</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import apiClient from '@/api/client'

interface KeySummary {
  activeKeyId: string | null
  allKeyIds: string[]
  deprecatedKeyIds: string[]
}

interface KeyRow {
  keyId: string
  status: 'ACTIVE' | 'DEPRECATED' | 'UNKNOWN'
}

const loading = ref(false)
const rotating = ref(false)
const summary = ref<KeySummary>({ activeKeyId: null, allKeyIds: [], deprecatedKeyIds: [] })

const keyRows = computed<KeyRow[]>(() => {
  const deprecated = new Set(summary.value.deprecatedKeyIds ?? [])
  return (summary.value.allKeyIds ?? []).map((keyId) => ({
    keyId,
    status: keyId === summary.value.activeKeyId
      ? 'ACTIVE'
      : deprecated.has(keyId)
        ? 'DEPRECATED'
        : 'UNKNOWN',
  }))
})

function statusLabel(status: string) {
  const map: Record<string, string> = { ACTIVE: '活跃', DEPRECATED: '已停用', UNKNOWN: '未知' }
  return map[status] ?? status
}

function tagType(status: string) {
  const map: Record<string, string> = { ACTIVE: 'success', DEPRECATED: 'warning', UNKNOWN: 'info' }
  return map[status] ?? 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await apiClient.get<KeySummary>('/keys')
    summary.value = data
  } catch {
    summary.value = { activeKeyId: null, allKeyIds: [], deprecatedKeyIds: [] }
  } finally {
    loading.value = false
  }
}

async function rotateKey() {
  try {
    await ElMessageBox.confirm('确定要轮换密钥吗？将生成新的活跃密钥，当前活跃密钥转为停用。', '确认轮换', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  rotating.value = true
  try {
    const { data } = await apiClient.post<{ newKeyId: string }>('/keys/rotate')
    ElMessage.success(`密钥已轮换，新密钥：${data.newKeyId}`)
    fetchData()
  } catch {
    ElMessage.error('轮换失败')
  } finally {
    rotating.value = false
  }
}

async function removeKey(row: KeyRow) {
  try {
    await ElMessageBox.confirm(`确定要删除停用密钥 "${row.keyId}" 吗？此操作不可撤销。`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'error',
    })
  } catch {
    return
  }
  try {
    await apiClient.delete(`/keys/${encodeURIComponent(row.keyId)}`)
    ElMessage.success('密钥已删除')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.muted {
  color: var(--el-text-color-placeholder);
}
</style>
