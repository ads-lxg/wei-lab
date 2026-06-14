import { get } from './request'
import type { SearchResult } from '@/types'

/** GET /api/search */
export function search(
  keyword: string,
  type: string = 'all',
  page: number = 1,
  size: number = 10,
): Promise<SearchResult> {
  return get<SearchResult>('/search', { keyword, type, page, size } as unknown as Record<string, unknown>)
}
