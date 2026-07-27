<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建用户
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showRoleDialog(row)">角色</el-button>
            <el-button
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
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

    <!-- Create User Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建用户" width="460px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="createForm.displayName" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="审批人" value="APPROVER" />
            <el-option label="操作员" value="OPERATOR" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- Role Assignment Dialog -->
    <el-dialog v-model="roleDialogVisible" title="角色分配" width="400px">
      <el-form label-width="80px">
        <el-form-item label="用户">
          <el-input :model-value="editingUser?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editingRole" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="审批人" value="APPROVER" />
            <el-option label="操作员" value="OPERATOR" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="assignRole">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import apiClient from '@/api/client'
import type { User } from '@/types/api'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

// Create dialog
const createDialogVisible = ref(false)
const createForm = reactive({
  username: '',
  displayName: '',
  password: '',
  role: 'OPERATOR',
})

// Role dialog
const roleDialogVisible = ref(false)
const editingUser = ref<User | null>(null)
const editingRole = ref('')

function showCreateDialog() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.password = ''
  createForm.role = 'OPERATOR'
  createDialogVisible.value = true
}

function showRoleDialog(user: User) {
  editingUser.value = user
  editingRole.value = user.role
  roleDialogVisible.value = true
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await apiClient.get<User[]>('/users', {
      params: { page: page.value - 1, size: pageSize.value },
    })
    const list = Array.isArray(data) ? data : []
    tableData.value = list
    total.value = list.length
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function createUser() {
  if (!createForm.username || !createForm.password) {
    ElMessage.warning('请填写必填字段')
    return
  }
  submitting.value = true
  try {
    await apiClient.post('/users', createForm)
    ElMessage.success('用户创建成功')
    createDialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

async function assignRole() {
  if (!editingUser.value) return
  submitting.value = true
  try {
    await apiClient.put(`/users/${editingUser.value.id}/role`, { role: editingRole.value })
    ElMessage.success('角色已更新')
    roleDialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('更新失败')
  } finally {
    submitting.value = false
  }
}

async function toggleEnabled(user: User) {
  const action = user.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 "${user.username}" 吗？`, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await apiClient.put(`/users/${user.id}/enabled`, { enabled: !user.enabled })
    ElMessage.success(`已${action}`)
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
