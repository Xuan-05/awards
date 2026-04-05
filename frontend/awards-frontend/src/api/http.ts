import axios from 'axios'

/**
 * Axios 实例（前端统一 HTTP 客户端）。
 *
 * <p>约定：</p>
 * - baseURL 固定为 {@code /api}，由 Vite 代理转发到后端（开发）或由同域反代（生产）。
 * - 登录成功后 token 存在 localStorage 的 {@code token} 键中。
 *
 * <p>拦截器行为：</p>
 * - 请求拦截：若存在 token，则注入 {@code Authorization: Bearer <token>}。
 * - 响应拦截：若遇到 HTTP 401（未登录/登录失效），清理 token 并强制跳转到 /login。
 *
 * <p>注意：401 处理采用 {@code window.location.replace}，避免 router 循环依赖。</p>
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
})

http.interceptors.request.use((config) => {
  // 从 localStorage 读取 token（登录成功后写入）
  const token = localStorage.getItem('token')
  // 统一使用 Bearer 方案，与后端 Sa-Token 的 token-prefix=Bearer 对齐
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp) => resp,
  async (error) => {
    // 后端返回 401：视为登录失效，清理本地 token 并回到登录页
    if (error?.response?.status === 401) {
      localStorage.removeItem('token')
      // Avoid circular import with router: hard redirect to login.
      if (window.location.pathname !== '/login') {
        window.location.replace('/login')
      }
    }
    return Promise.reject(error)
  },
)

