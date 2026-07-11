<template>
  <el-dialog
    title="新建诊断会话"
    :model-value="visible"
    width="480px"
    @update:model-value="$emit('update:visible', $event)"
    @close="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="目标应用" prop="targetApp">
        <el-input v-model="form.targetApp" placeholder="例如：user-service" />
      </el-form-item>
      <el-form-item label="目标主机" prop="targetHost">
        <el-input v-model="form.targetHost" placeholder="例如：192.168.1.100" />
      </el-form-item>
      <el-form-item label="目标端口" prop="targetPort">
        <el-input-number
          v-model="form.targetPort"
          :min="1"
          :max="65535"
          controls-position="right"
          placeholder="8080"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import apiClient from '@/api/client'
import type { DiagnosisSession } from '@/types/diagnosis'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  created: [session: DiagnosisSession]
}>()

const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const form = reactive({
  targetApp: '',
  targetHost: '',
  targetPort: 8080,
})

const rules: FormRules = {
  targetApp: [{ required: true, message: '请输入目标应用名称', trigger: 'blur' }],
  targetHost: [{ required: true, message: '请输入目标主机地址', trigger: 'blur' }],
  targetPort: [{ required: true, message: '请输入目标端口', trigger: 'blur' }],
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    const { data } = await apiClient.post('/diagnosis/sessions', {
      targetApp: form.targetApp,
      targetHost: form.targetHost,
      targetPort: form.targetPort,
    })
    ElMessage.success('会话创建成功')
    emit('created', data)
  } catch {
    ElMessage.error('会话创建失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.targetApp = ''
  form.targetHost = ''
  form.targetPort = 8080
  formRef.value?.resetFields()
}
</script>
