import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFolderTree, createFolder, updateFolder, deleteFolder, moveFolder } from '@/api/folder'
import type { FolderTreeVO, FolderCreateDTO, FolderUpdateDTO, FolderMoveDTO } from '@/types'

export const useFolderStore = defineStore('folder', () => {
  const tree = ref<FolderTreeVO[]>([])
  const selectedFolderId = ref<number | null>(null)
  const loading = ref(false)

  async function fetchTree() {
    loading.value = true
    try {
      tree.value = await getFolderTree()
    } finally {
      loading.value = false
    }
  }

  async function doCreate(data: FolderCreateDTO) {
    await createFolder(data)
    await fetchTree()
  }

  async function doUpdate(data: FolderUpdateDTO) {
    await updateFolder(data)
    await fetchTree()
  }

  async function doDelete(id: number) {
    await deleteFolder(id)
    await fetchTree()
  }

  async function doMove(data: FolderMoveDTO) {
    await moveFolder(data)
    await fetchTree()
  }

  function selectFolder(id: number | null) {
    selectedFolderId.value = id
  }

  return { tree, selectedFolderId, loading, fetchTree, doCreate, doUpdate, doDelete, doMove, selectFolder }
})
