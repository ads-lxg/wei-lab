import { get, post } from './request'

/** GET /api/config/list */
export function listConfigs(): Promise<Record<string, string>> {
  return get('/config/list')
}

/** GET /api/config/{key} */
export function getConfig(key: string): Promise<string> {
  return get(`/config/${key}`)
}

/** POST /api/config/update */
export function updateConfigs(configs: Record<string, string>): Promise<void> {
  return post('/config/update', configs)
}
