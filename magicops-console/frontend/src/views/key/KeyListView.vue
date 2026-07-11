<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>密钥管理</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建密钥
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="密钥名称" width="180" />
        <el-table-column prop="keyType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.keyType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '活跃' : '已吊销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fingerprint" label="指纹" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column prop="expiresAt" label="过期时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="rotateKey(row)">轮换</el-button>
            <el-button size="small" type="danger" @click="removeKey(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- Create Key Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建密钥" width="460px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.keyType" style="width: 100%">
            <el-option label="RSA" value="RSA" />
            <el-option label="AES" value="AES" />
            <el-option label="HMAC" value="HMAC" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createKey">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import apiClient from '@/api/client'
import type { KeyEntry, PageResponse } from '@/types/api'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<KeyEntry[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const createDialogVisible = ref(false)
const createForm = reactive({
  name: '',
  keyType: 'RSA',
})

function showCreateDialog() {
  createForm.name = ''
  createForm.keyType = 'RSA'
  createDialogVisible.value = true
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await apiClient.get<PageResponse<KeyEntry>>('/keys', {
      params: { page: page.value - 1, size: pageSize.value },
    })
    tableData.value = data.content
    total.value = data.totalElements
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function createKey() {
  if (!createForm.name) {
    ElMessage.warning('请输入密钥名称')
    return
  }
  submitting.value = true
  try {
    await apiClient.post('/keys', createForm)
    ElMessage.success('密钥创建成功')
    createDialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

async function rotateKey(key: KeyEntry) {
  try {
    await ElMessageBox.confirm(`确定要轮换密钥 "${key.name}" 吗？`, '确认轮换', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await apiClient.post(`/keys/${key.id}/rotate`)
    ElMessage.success('密钥已轮换')
    fetchData()
  } catch {
    // cancelled or failed
  }
}

async function removeKey(key: KeyEntry) {
  try {
    await ElMessageBox.confirm(`确定要删除密钥 "${key.name}" 吗？此操作不可撤销。`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'error',
    })
    await apiClient.delete(`/keys/${key.id}`)
    ElMessage.success('密钥已删除')
    fetchData()
  } catch {
    // cancelled or failed
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
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
