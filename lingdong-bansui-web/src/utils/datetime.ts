/**
 * 统一的日期时间显示格式：YYYY-MM-DD HH:mm:ss（如 2026-10-02 09:24:12）。
 * 后端返回 LocalDateTime 序列化文本（如 2026-10-02T09:24:12），不做时区换算。
 * 仅保留单参数签名，以便直接用作表格列 render。
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-';
  const [date, time] = value.replace('T', ' ').split(' ');
  if (!time) return date;
  const [hour = '00', minute = '00', second = '00'] = time.split(':');
  return `${date} ${hour}:${minute}:${second}`;
}
