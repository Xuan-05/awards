/**
 * 后端统一响应结构。
 *
 * <p>约定：</p>
 * - {@code code === 0} 表示成功\n
 * - 失败时 {@code code != 0}，{@code message} 为错误信息，{@code data} 可能为 null\n
 *
 * <p>注意：HTTP 状态码与业务 code 可能同时存在；例如未登录时后端也可能返回 401。</p>
 */
export type ApiResponse<T> = {
  /** 业务码：0 成功，非 0 失败 */
  code: number
  /** 提示信息：成功通常为 OK，失败为错误原因 */
  message: string
  /** 业务数据：失败时可能为 null */
  data: T
}

/**
 * 后端分页结构。
 *
 * <p>约定：</p>
 * - {@code total}：总条数\n
 * - {@code list}：当前页数据\n
 */
export type PageResult<T> = {
  /** 总条数 */
  total: number
  /** 当前页数据 */
  list: T[]
}

