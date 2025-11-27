<!-- views/UploadView.vue -->
<template>
  <div class="upload-container">
    <h2 class="page-title">添加小说</h2>

    <el-row :gutter="20">
      <el-col :span="24" :lg="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <h3>输入小说ID</h3>
            </div>
          </template>

          <div class="upload-form">
            <p class="upload-tip">请输入您想要添加的小说ID，系统将自动抓取小说内容。</p>

            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              @submit.prevent="handleAddNovel"
            >
              <el-form-item label="小说ID" prop="novelId">
                <el-input v-model="form.novelId" placeholder="请输入小说ID" clearable />
              </el-form-item>

              <div class="upload-buttons">
                <el-button type="primary" @click="handleAddNovel" :loading="novelStore.isLoading">
                  添加小说
                </el-button>
                <el-button @click="searchInstead">搜索小说</el-button>
              </div>

              <div class="upload-error" v-if="novelStore.error">
                {{ novelStore.error }}
              </div>
            </el-form>
          </div>
        </el-card>

        <!-- 简化的任务状态通知 -->
        <el-result
          v-if="addSuccess"
          icon="success"
          title="小说添加成功"
          sub-title="任务已提交到后台处理，您可以在任务管理页面查看进度"
        >
          <template #extra>
            <el-button type="primary" @click="viewTasks">查看任务进度</el-button>
            <el-button @click="resetForm">添加新小说</el-button>
          </template>
        </el-result>
      </el-col>

      <el-col :span="24" :lg="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <h3>操作指南</h3>
            </div>
          </template>

          <div class="instructions">
            <h4>如何添加小说</h4>
            <ol>
              <li>输入正确的小说ID</li>
              <li>点击"添加小说"按钮</li>
              <li>系统将自动抓取小说内容</li>
              <li>抓取完成后，小说将被添加到您的书库</li>
            </ol>

            <h4>找不到小说ID？</h4>
            <p>
              您可以使用<router-link to="/search">搜索功能</router-link
              >来查找小说，然后将其添加到书库。
            </p>

            <h4>注意事项</h4>
            <ul>
              <li>抓取过程可能需要一些时间，请耐心等待</li>
              <li>抓取完成后，您可以在<router-link to="/novels">小说列表</router-link>中查看</li>
              <li>您可以在<router-link to="/tasks">任务管理</router-link>页面查看下载进度</li>
              <li>如果添加失败，您可以尝试重新添加或联系管理员</li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNovelStore, useTaskStore } from '../store'

const router = useRouter()
const novelStore = useNovelStore()
const taskStore = useTaskStore() // 添加任务 store 用于通知
const formRef = ref()
const addSuccess = ref(false)

// 页面状态
const form = reactive({
  novelId: null as string | null,
})

const rules = {
  novelId: [{ required: true, message: '请输入小说ID', trigger: 'blur' }],
}

// 处理添加小说
const handleAddNovel = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid: boolean) => {
    if (!valid || form.novelId === null) {
      ElMessage.warning('请输入有效的小说ID')
      return
    }

    try {
      const response = await novelStore.addNovel(form.novelId)

      if (response) {
        ElMessage.success('小说添加成功，开始下载')
        addSuccess.value = true
      }
    } catch (err) {
      ElMessage.error('添加小说失败')
    }
  })
}

// 重置表单
const resetForm = () => {
  form.novelId = null
  novelStore.error = null
  addSuccess.value = false

  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 前往任务管理页面
const viewTasks = () => {
  router.push('/tasks')
}

// 前往搜索页面
const searchInstead = () => {
  router.push('/search')
}

onMounted(() => {
  // 重置表单状态
  resetForm()
})
</script>

<style scoped>
.upload-container {
  padding-bottom: 20px;
}

.page-title {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
}

.upload-form {
  padding: 10px 0;
}

.upload-tip {
  margin-bottom: 20px;
  color: #606266;
}

.upload-buttons {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.upload-error {
  margin-top: 15px;
  color: #f56c6c;
}

.instructions {
  line-height: 1.6;
}

.instructions h4 {
  margin-top: 20px;
  margin-bottom: 10px;
}

.instructions ol,
.instructions ul {
  padding-left: 20px;
  margin-bottom: 15px;
}

.instructions li {
  margin-bottom: 5px;
}

.instructions a {
  color: #409eff;
  text-decoration: none;
}

.instructions a:hover {
  text-decoration: underline;
}
</style>
