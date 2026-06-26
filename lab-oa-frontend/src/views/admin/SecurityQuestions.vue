<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAllQuestions, createQuestion, updateQuestion, deleteQuestion, batchDeleteQuestions, type SecurityQuestionVO } from '@/api/security-question'

const loading = ref(false)
const questions = ref<SecurityQuestionVO[]>([])
const selectedIds = ref<number[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ id: 0, question: '', answer: '', status: 1 })

function openCreate() {
  isEdit.value = false
  form.value = { id: 0, question: '', answer: '', status: 1 }
  dialogVisible.value = true
}

function openEdit(q: SecurityQuestionVO) {
  isEdit.value = true
  form.value = { id: q.id, question: q.question, answer: q.answer, status: q.status ?? 1 }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.question.trim() || !form.value.answer.trim()) {
    ElMessage.warning('问题和答案不能为空')
    return
  }
  if (isEdit.value && form.value.id) {
    await updateQuestion(form.value.id, { question: form.value.question, answer: form.value.answer, status: form.value.status })
    ElMessage.success('更新成功')
  } else {
    await createQuestion({ question: form.value.question, answer: form.value.answer })
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
  fetchData()
}

async function handleToggleStatus(row: SecurityQuestionVO) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateQuestion(row.id, { question: row.question, answer: row.answer, status: newStatus })
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

async function handleDelete(q: SecurityQuestionVO) {
  try {
    await ElMessageBox.confirm(`确认删除该安全问题？`, '删除', { type: 'warning' })
    await deleteQuestion(q.id)
    ElMessage.success('删除成功')
    selectedIds.value = selectedIds.value.filter(id => id !== q.id)
    fetchData()
  } catch { /* cancelled */ }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的题目')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 道题目？`, '批量删除', { type: 'warning' })
    await batchDeleteQuestions(selectedIds.value)
    ElMessage.success(`成功删除 ${selectedIds.value.length} 道题目`)
    selectedIds.value = []
    fetchData()
  } catch { /* cancelled */ }
}

function handleSelectAll(val: boolean) {
  selectedIds.value = val ? questions.value.map(q => q.id) : []
}

function handleSelectionChange(ids: SecurityQuestionVO[]) {
  selectedIds.value = ids.map(q => q.id)
}

async function fetchData() {
  loading.value = true
  try {
    questions.value = await listAllQuestions()
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-5 animate-fade-in">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold" style="color: var(--text-primary)">题库管理</h1>
        <p class="text-sm mt-1" style="color: var(--text-muted)">管理注册时使用的安全问题题库</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchDelete">
          <el-icon :size="14"><Delete /></el-icon> 批量删除 ({{ selectedIds.length }})
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon :size="14"><Plus /></el-icon> 新建问题
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-table
        :data="questions" v-loading="loading" size="small" stripe row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column label="问题" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-sm" style="color: var(--text-primary)">{{ row.question }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="answer" label="答案" width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140" align="center">
          <template #default="{ row }">{{ row.createTime?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑问题' : '新建问题'" width="460px">
      <el-form size="small" label-width="60px">
        <el-form-item label="问题"><el-input v-model="form.question" placeholder="安全问题" /></el-form-item>
        <el-form-item label="答案"><el-input v-model="form.answer" placeholder="正确答案" /></el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
