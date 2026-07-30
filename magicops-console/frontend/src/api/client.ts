import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 为 true 时不弹出全局错误提示,由调用方自行处理 */
    skipErrorMessage?: boolean
  }
}

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 从后端响应中提取可读错误信息(后端约定 { error: "..." })
function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { error?: string; message?: string } | undefined
    if (data?.error) return data.error
    if (data?.message) return data.message
    if (error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK') {
      return '网络异常,请检查连接后重试'
    }
  }
  return '请求失败,请稍后重试'
}

// Response interceptor:
// - 401 统一跳转登录页(登录接口自身的 401 除外)
// - 其余错误统一弹出错误提示;调用方可在 config 中传 skipErrorMessage 自行处理
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const config = error.config
    if (error.response?.status === 401 && !config?.url?.endsWith('/login')) {
      // 跳登录页时保留原目标路径,登录后可回跳
      if (router.currentRoute.value.path !== '/login') {
        router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    if (!config?.skipErrorMessage) {
      ElMessage.error(extractErrorMessage(error))
    }
    return Promise.reject(error)
  },
)

export default apiClient
