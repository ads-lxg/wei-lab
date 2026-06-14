import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listByFolder, searchDocuments, getDocumentDetail } from '@/api/document'
import type {
  LiteratureListItemVO,
  LiteratureDetailVO,
  DocumentFolderQueryDTO,
  DocumentSearchDTO,
  PageResult,
} from '@/types'

export const useDocumentStore = defineStore('document', () => {
  const loading = ref(false)
  const documents = ref<LiteratureListItemVO[]>([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const currentDetail = ref<LiteratureDetailVO | null>(null)

  async function fetchByFolder(params: DocumentFolderQueryDTO) {
    loading.value = true
    try {
      const result: PageResult<LiteratureListItemVO> = await listByFolder(params)
      documents.value = result.records
      total.value = result.total
      currentPage.value = result.page
      pageSize.value = result.size
    } finally {
      loading.value = false
    }
  }

  async function searchDocs(params: DocumentSearchDTO) {
    loading.value = true
    try {
      const result: PageResult<LiteratureListItemVO> = await searchDocuments(params)
      documents.value = result.records
      total.value = result.total
      currentPage.value = result.page
      pageSize.value = result.size
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number) {
    currentDetail.value = await getDocumentDetail(id)
    return currentDetail.value
  }

  function clearList() {
    documents.value = []
    total.value = 0
  }

  return {
    loading, documents, total, currentPage, pageSize, currentDetail,
    fetchByFolder, searchDocs, fetchDetail, clearList,
  }
})
