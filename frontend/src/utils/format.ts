/** 后端 LocalDateTime 默认序列化为 ISO 字符串(如 2026-06-01T12:34:56)。 */
export function formatDateTime(v?: string | null): string {
  if (!v) return '-'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

export function formatNumber(n?: number | null): string {
  if (n === null || n === undefined) return '0'
  return n.toLocaleString('en-US')
}

/** 额度展示:0 表示不限额 */
export function formatQuota(used?: number | null, total?: number | null): string {
  const u = formatNumber(used ?? 0)
  if (!total || total <= 0) return `${u} / 不限`
  return `${u} / ${formatNumber(total)}`
}

/** <input type="datetime-local"> 的值(yyyy-MM-ddTHH:mm)补足秒给后端 */
export function localInputToIso(v: string): string | undefined {
  if (!v) return undefined
  return v.length === 16 ? `${v}:00` : v
}

/** 后端 ISO 时间转 <input type="datetime-local"> 可用值(yyyy-MM-ddTHH:mm) */
export function isoToLocalInput(v?: string | null): string {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}
