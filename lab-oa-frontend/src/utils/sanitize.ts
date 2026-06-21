/**
 * 简易 HTML 转义，防止用户输入被当作 HTML 解析
 */
export function escapeHtml(text: string): string {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * 校验 URL 协议，拒绝 javascript: data: vbscript: 等危险协议
 */
export function isDangerousUrl(url: string): boolean {
  if (!url) return true
  const protocol = url.trim().split(':')[0].toLowerCase()
  return ['javascript', 'data', 'vbscript', 'file'].includes(protocol)
}
