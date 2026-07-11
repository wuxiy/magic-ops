<template>
  <div class="command-form">
    <template v-for="param in template.parameters" :key="param.name">
      <el-input
        v-if="param.type === 'string'"
        v-model="localParams[param.name]"
        :placeholder="param.placeholder || param.label"
        size="small"
        style="width: 140px"
      />
      <el-input-number
        v-else-if="param.type === 'number'"
        v-model="localParams[param.name]"
        :placeholder="param.placeholder || param.label"
        size="small"
        controls-position="right"
        style="width: 120px"
      />
      <el-switch
        v-else-if="param.type === 'boolean'"
        v-model="localParams[param.name]"
        :active-text="param.label"
        size="small"
      />
      <el-select
        v-else-if="param.type === 'select'"
        v-model="localParams[param.name]"
        :placeholder="param.placeholder || param.label"
        size="small"
        style="width: 140px"
      >
        <el-option
          v-for="opt in param.options"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { CommandTemplate } from '@/types/diagnosis'

const props = defineProps<{
  template: CommandTemplate
  params: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:params': [value: Record<string, unknown>]
}>()

const localParams = reactive<Record<string, unknown>>({})

watch(
  () => props.template,
  (tpl) => {
    // Reset params when template changes
    for (const key of Object.keys(localParams)) {
      delete localParams[key]
    }
    for (const p of tpl.parameters) {
      localParams[p.name] = p.defaultValue ?? (p.type === 'boolean' ? false : '')
    }
  },
  { immediate: true },
)

watch(
  localParams,
  (val) => {
    emit('update:params', { ...val })
  },
  { deep: true },
)
</script>

<style scoped>
.command-form {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
