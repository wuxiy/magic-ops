<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <h1 class="page-title">用户管理</h1>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建用户
          </el-button>
        </div>
      </template>

      <el-table :data="pagedUsers" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
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
            <el-button size="small" @click="showRoleDialog(row as ManagedUser)">角色</el-button>
            <el-button
              size="small"
              :type="row.enabled ? 'warning' : 'success'"
              @click="toggleEnabled(row as ManagedUser)"
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
        />
      </div>
    </el-card>

    <!-- Create User Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建用户" width="460px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="createForm.displayName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createForm.email" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- Role Management Dialog -->
    <el-dialog v-model="roleDialogVisible" title="角色管理" width="460px">
      <div v-loading="rolesLoading">
        <div class="role-current">
          <span class="role-label">用户</span>
          <span>{{ editingUser?.username }}</span>
        </div>
        <div class="role-current">
          <span class="role-label">当前角色</span>
          <template v-if="currentRoles.length">
            <el-tag v-for="role in currentRoles" :key="role.id" class="role-tag">
              {{ role.displayName || role.name }}
            </el-tag>
          </template>
          <span v-else class="role-empty">暂未分配角色</span>
        </div>
        <el-form label-width="80px" class="role-assign-form">
          <el-form-item label="新增角色">
            <el-select v-model="selectedRoleName" placeholder="选择要分配的角色" style="width: 100%">
              <el-option
                v-for="role in assignableRoles"
                :key="role.id"
                :label="role.displayName || role.name"
                :value="role.name"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!selectedRoleName"
          :loading="submitting"
          @click="assignRole"
        >
          分配
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import apiClient from '@/api/client'
import type { ManagedUser, Role } from '@/types/api'

const loading = ref(false)
const submitting = ref(false)

// 后端 /api/users 返回全量列表,分页在前端进行
const allUsers = ref<ManagedUser[]>([])
const page = ref(1)
const pageSize = ref(20)

const total = computed(() => allUsers.value.length)
const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return allUsers.value.slice(start, start + pageSize.value)
})

// Create dialog
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  displayName: '',
  email: '',
  password: '',
})

const createRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// Role dialog
const roleDialogVisible = ref(false)
const rolesLoading = ref(false)
const editingUser = ref<ManagedUser | null>(null)
const currentRoles = ref<Role[]>([])
const allRoles = ref<Role[]>([])
const selectedRoleName = ref('')

// 过滤掉用户已拥有的角色,避免重复分配
const assignableRoles = computed(() => {
  const owned = new Set(currentRoles.value.map(r => r.name))
  return allRoles.value.filter(r => !owned.has(r.name))
})

function showCreateDialog() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.email = ''
  createForm.password = ''
  createDialogVisible.value = true
}

async function showRoleDialog(user: ManagedUser) {
  editingUser.value = user
  selectedRoleName.value = ''
  roleDialogVisible.value = true
  rolesLoading.value = true
  try {
    const [userRoles, roles] = await Promise.all([
      apiClient.get<Role[]>(`/users/${user.id}/roles`),
      apiClient.get<Role[]>('/roles'),
    ])
    currentRoles.value = userRoles.data
    allRoles.value = roles.data
  } catch {
    // 加载失败:错误提示由响应拦截器统一展示,对话框内显示空态
    currentRoles.value = []
    allRoles.value = []
  } finally {
    rolesLoading.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await apiClient.get<ManagedUser[]>('/users')
    allUsers.value = Array.isArray(data) ? data : []
  } catch {
    // 请求失败:保留当前数据,错误提示由响应拦截器统一展示
  } finally {
    loading.value = false
  }
}

async function createUser() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await apiClient.post('/users', createForm)
    ElMessage.success('用户创建成功,可在"角色"中为其分配角色')
    createDialogVisible.value = false
    fetchData()
  } catch {
    // 错误提示由响应拦截器统一展示
  } finally {
    submitting.value = false
  }
}

async function assignRole() {
  if (!editingUser.value || !selectedRoleName.value) return
  submitting.value = true
  try {
    await apiClient.post(`/users/${editingUser.value.id}/roles`, {
      roleName: selectedRoleName.value,
    })
    ElMessage.success('角色已分配')
    selectedRoleName.value = ''
    // 刷新对话框内的角色列表
    const { data } = await apiClient.get<Role[]>(`/users/${editingUser.value.id}/roles`)
    currentRoles.value = data
  } catch {
    // 错误提示由响应拦截器统一展示
  } finally {
    submitting.value = false
  }
}

async function toggleEnabled(user: ManagedUser) {
  const action = user.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 "${user.username}" 吗?`, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  try {
    await apiClient.put(`/users/${user.id}/enabled`, { enabled: !user.enabled })
    ElMessage.success(`已${action}`)
    fetchData()
  } catch {
    // 错误提示由响应拦截器统一展示
  }
}

onMounted(fetchData)
</script>

<style scoped>
.role-current {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.role-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--el-text-color-regular);
}
.role-tag {
  margin-right: 4px;
}
.role-empty {
  color: var(--el-text-color-secondary);
}
.role-assign-form {
  margin-top: 4px;
}
</style>
