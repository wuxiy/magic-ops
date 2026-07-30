<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <h1 class="page-title">发布历史</h1>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="packageName" label="发布包" width="200" />
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="environment" label="环境" width="120">
          <template #default="{ row }">
            <el-tag :type="envTagType(row.environment)">{{ row.environment }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishedBy" label="发布人" width="120" />
        <el-table-column prop="publishedAt" label="发布时间" width="180" />
        <el-table-column prop="approvalId" label="审批ID" width="100" />
        <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip />
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
import type { PublishRecord, PageResponse } from '@/types/api'

const loading = ref(false)
const tableData = ref<PublishRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

function envTagType(env: string) {
  switch (env) {
    case 'production': return 'danger'
    case 'staging': return 'warning'
    default: return 'info'
  }
}

function statusTagType(status: string) {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'ROLLBACK': return 'warning'
    default: return 'info'
  }
}

function statusLabel(status: string) {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'ROLLBACK': return '已回滚'
    default: return status
  }
}

async function fetchData() {
  loading.value = true
  try {
    const { data } = await apiClient.get<PageResponse<PublishRecord>>('/publish', {
      params: { page: page.value - 1, size: pageSize.value },
    })
    tableData.value = data.content
    total.value = data.totalElements
  } catch {
    // 请求失败:保留当前数据,错误提示由响应拦截器统一展示
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

