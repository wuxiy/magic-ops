<template>
  <el-select
    :model-value="modelValue?.id ?? ''"
    placeholder="选择命令模板"
    filterable
    clearable
    @update:model-value="onSelect"
  >
    <el-option-group
      v-for="group in groupedTemplates"
      :key="group.label"
      :label="group.label"
    >
      <el-option
        v-for="tpl in group.items"
        :key="tpl.id"
        :label="tpl.name"
        :value="tpl.id"
      >
        <span>{{ tpl.name }}</span>
        <el-tag
          :type="riskTagType(tpl.riskLevel)"
          size="small"
          style="float: right; margin-left: 8px"
        >
          {{ riskLabel(tpl.riskLevel) }}
        </el-tag>
      </el-option>
    </el-option-group>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import apiClient from '@/api/client'
import type { CommandTemplate } from '@/types/diagnosis'

defineProps<{
  modelValue: CommandTemplate | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: CommandTemplate | null]
}>()

const templates = ref<CommandTemplate[]>([])

const groupedTemplates = computed(() => {
  const groups: Record<string, CommandTemplate[]> = {}
  for (const tpl of templates.value) {
    const key = tpl.category || '通用'
    if (!groups[key]) groups[key] = []
    groups[key].push(tpl)
  }
  return Object.entries(groups).map(([label, items]) => ({ label, items }))
})

function riskTagType(level: string) {
  const map: Record<string, string> = { safe: 'success', caution: 'warning', dangerous: 'danger' }
  return map[level] || 'info'
}

function riskLabel(level: string) {
  const map: Record<string, string> = { safe: '安全', caution: '注意', dangerous: '危险' }
  return map[level] || level
}

function onSelect(id: string) {
  if (!id) {
    emit('update:modelValue', null)
    return
  }
  const tpl = templates.value.find((t) => t.id === id) ?? null
  emit('update:modelValue', tpl)
}

onMounted(async () => {
  try {
    const { data } = await apiClient.get('/diagnosis/templates')
    templates.value = data
  } catch {
    ElMessage.error('加载命令模板失败')
  }
})
</script>
