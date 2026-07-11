<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>审批管理</span>
          <el-select v-model="decisionFilter" placeholder="审批状态" clearable style="width: 160px" @change="fetchData">
            <el-option label="全部" value="" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="待审批" value="PENDING" />
          </el-select>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="entityType" label="实体类型" width="120" />
        <el-table-column prop="entityId" label="实体ID" width="120" />
        <el-table-column prop="action" label="操作" width="120" />
        <el-table-column prop="requestedBy" label="申请人" width="120" />
        <el-table-column prop="decision" label="审批结果" width="100">
          <template #default="{ row }">
            <el-tag :type="decisionTagType(row.decision)">{{ decisionLabel(row.decision) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="decidedBy" label="审批人" width="120" />
        <el-table-column prop="createdAt" label="申请时间" width="180" />
        <el-table-column prop="decidedAt" label="审批时间" width="180" />
        <el-table-column prop="comment" label="备注" min-width="160" show-overflow-tooltip />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import apiClient from '@/api/client'
import type { Approval, PageResponse } from '@/types/api'

const loading = ref(false)
const tableData = ref<Approval[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const decisionFilter = ref('')

function decisionTagType(decision: string) {
  switch (decision) {
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    default: return 'warning'
  }
}

function decisionLabel(decision: string) {
  switch (decision) {
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已拒绝'
    case 'PENDING': return '待审批'
    default: return decision
  }
}

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value - 1,
      size: pageSize.value,
    }
    if (decisionFilter.value) {
      params.decision = decisionFilter.value
    }
    const { data } = await apiClient.get<PageResponse<Approval>>('/approvals', { params })
    tableData.value = data.content
    total.value = data.totalElements
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.page-container {
  padding: 0;
}
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
