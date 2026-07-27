<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>审计查询</span>
          <div class="filter-bar">
            <el-input
              v-model="entityFilter"
              placeholder="实体类型"
              clearable
              style="width: 150px"
              @clear="fetchData"
              @keyup.enter="fetchData"
            />
            <el-input
              v-model="eventFilter"
              placeholder="事件类型"
              clearable
              style="width: 150px"
              @clear="fetchData"
              @keyup.enter="fetchData"
            />
            <el-button type="primary" @click="fetchData">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="entityType" label="实体类型" width="120" />
        <el-table-column prop="entityId" label="实体ID" width="120" />
        <el-table-column prop="eventType" label="事件类型" width="120" />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="eventTimestamp" label="时间" width="200" />
        <el-table-column prop="details" label="详情" min-width="200" show-overflow-tooltip />
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
import type { AuditRecord, PageResponse } from '@/types/api'

const loading = ref(false)
const tableData = ref<AuditRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const entityFilter = ref('')
const eventFilter = ref('')

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value - 1,
      size: pageSize.value,
    }
    if (entityFilter.value) params.entityType = entityFilter.value
    if (eventFilter.value) params.eventType = eventFilter.value

    const { data } = await apiClient.get<PageResponse<AuditRecord>>('/audit', { params })
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
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
