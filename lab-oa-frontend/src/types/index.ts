// ===== Common Response Types =====

/** 通用后端响应 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

/** 分页查询参数 */
export interface PageQuery {
  page?: number
  size?: number
}

// ===== Auth Types =====

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  email: string
  username: string
  password: string
  realName?: string
}

export interface LoginVO {
  token: string
  user: UserVO
}

// ===== User Types =====

export interface UserVO {
  id: number
  username: string
  email: string
  phone: string
  realName: string
  avatar: string
  status: number
  roles: string[]
  createTime: string
}

export interface UserPageDTO extends PageQuery {
  keyword?: string
  status?: number
}

// ===== Role Types =====

export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  description: string
  createTime: string
  updateTime: string
}

// ===== Permission Types =====

export interface SysPermission {
  id: number
  permCode: string
  permName: string
  parentId: number
  type: string
  path: string
  icon: string
  sort: number
}

// ===== Folder Types =====

export interface FolderCreateDTO {
  parentId: number
  folderName: string
  sortOrder?: number
}

export interface FolderUpdateDTO {
  id: number
  folderName: string
}

export interface FolderMoveDTO {
  folderId: number
  targetParentId: number
}

export interface FolderTreeVO {
  id: number
  parentId: number
  folderName: string
  path: string
  levelNo: number
  sortOrder: number
  children: FolderTreeVO[]
  documents?: FolderDocumentVO[]
}

export interface FolderDocumentVO {
  id: number
  fileName: string
  title: string
  fileType: string
  parseStatus: string
}

// ===== Document Types =====

export interface LiteratureListItemVO {
  id: number
  fileName: string
  title: string
  authors: string
  keywords: string
  publishDate: string
  sourceJournal: string
  folderId: number
  folderName: string
  fileType: string
  createTime: string
  downloadCount: number
  parseStatus: string
}

export interface LiteratureDetailVO extends LiteratureListItemVO {
  abstractText: string
  doi: string
  uploaderId: number
  uploaderName: string
  viewCount: number
  ragSource: number
}

export interface LiteratureRecycleVO {
  id: number
  fileName: string
  title: string
  authors: string
  folderId: number
  folderName: string
  deleteTime: string
  createTime: string
}

export interface DocumentFolderQueryDTO extends PageQuery {
  folderId?: number
  keyword?: string
  sortField?: string
  sortOrder?: string
}

export interface DocumentSearchDTO extends PageQuery {
  keyword?: string
  sortField?: string
  sortOrder?: string
}

export interface DocumentBatchDeleteDTO {
  documentIds: number[]
}

export interface DocumentBatchMoveDTO {
  documentIds: number[]
  targetFolderId: number
}

export interface DocumentBatchRecoverDTO {
  documentIds: number[]
}

export interface BatchUploadResultVO {
  successCount: number
  failCount: number
  failList: { fileName: string; reason: string }[]
  successIds: number[]
}

// ===== Chat Types =====

export interface ChatRequest {
  sessionId?: string
  message: string
}

export interface SessionVO {
  sessionId: string
  title: string
  createTime: string
  updateTime: string
}

export interface ChatMessageVO {
  id: number
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  ragAvailable: boolean
  createTime: string
}

export interface Citation {
  referenceNumber: number
  fileName: string
  sourcePath: string
  chunkIndex: number
  excerpt: string
  docType: string
  docId: string
}

export interface DocumentChunkDTO {
  id: string
  chunkIndex: number
  content: string
  fileName: string
  sourcePath: string
}

// ===== Search Types =====

export interface SearchResult {
  total: number
  hits: SearchHit[]
}

export interface SearchHit {
  id: string
  docType: string
  docId: number
  fileName: string
  title: string
  content: string
  score: number
}

// ===== Notification Types =====

export interface Notification {
  id: number
  title: string
  content: string
  type: string
  referenceId: number
  isRead: number
  createTime: string
}

// ===== Dashboard Types =====

export interface DashboardStats {
  totalDocuments: number
  totalFolders: number
  totalDownloads: number
  todayNewDocuments: number
  hotDocuments: LiteratureListItemVO[]
  recentUploads: LiteratureListItemVO[]
  recentDownloads: LiteratureListItemVO[]
  systemStatus: SystemStatus
}

export interface SystemStatus {
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
  esStatus: 'green' | 'yellow' | 'red'
  ragStatus: 'online' | 'offline'
}

// ===== Common Types =====

/** MinIO 文件信息 */
export interface MinioFile {
  id: number
  originalName: string
  storedName: string
  bucket: string
  filePath: string
  fileSize: number
  mimeType: string
  md5: string
  uploaderId: number
  status: number
  createTime: string
}

export type ThemeMode = 'minimal' | 'zhuanti' | 'anime'

export interface AppConfig {
  theme: ThemeMode
  darkMode: boolean
  sidebarCollapsed: boolean
  language: 'zh-CN' | 'en-US'
}
