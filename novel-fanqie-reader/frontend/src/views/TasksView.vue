<!-- views/TasksView.vue -->
<template>
  <div class="tasks-container">
    <h2 class="page-title">任务管理</h2>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <h3>下载任务列表</h3>
              <div class="header-actions">
                <el-tag type="success" v-if="socketConnected">实时更新中</el-tag>
                <el-tag type="warning" v-else>实时更新未连接</el-tag>
                <el-button
                  type="primary"
                  size="small"
                  @click="refreshTasks"
                  :loading="taskStore.isLoading"
                >
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>
          </template>

          <div v-loading="taskStore.isLoading">
            <template v-if="taskStore.tasks.length === 0">
              <el-empty description="暂无任务">
                <el-button type="primary" @click="navigateToUpload">添加小说</el-button>
              </el-empty>
            </template>

            <el-table v-else :data="taskStore.tasks" style="width: 100%">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="task-details">
                    <el-descriptions :column="2" border>
                      <el-descriptions-item label="任务ID" :span="1">{{
                        row.id
                      }}</el-descriptions-item>
                      <el-descriptions-item label="Celery ID" :span="1">{{
                        row.celery_task_id || '-'
                      }}</el-descriptions-item>
                      <el-descriptions-item label="创建时间" :span="1">{{
                        formatDate(row.created_at)
                      }}</el-descriptions-item>
                      <el-descriptions-item label="更新时间" :span="1">{{
                        formatDate(row.updated_at)
                      }}</el-descriptions-item>
                      <el-descriptions-item label="状态信息" :span="2">{{
                        row.message || '-'
                      }}</el-descriptions-item>
                    </el-descriptions>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="小说信息" min-width="250">
                <template #default="{ row }">
                  <template v-if="row.novel">
                    <div class="novel-info">
                      <router-link :to="'/novel/' + String(row.novel_id)" class="novel-title">
                        {{ row.novel.title }}
                      </router-link>
                      <div class="novel-author">{{ row.novel.author || '未知作者' }}</div>
                    </div>
                  </template>
                  <template v-else>
                    <span>小说ID: {{ row.novel_id }}</span>
                  </template>
                </template>
              </el-table-column>

              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">
                    {{ getStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="进度" width="220">
                <template #default="{ row }">
                  <div class="task-progress-container">
                    <div
                      v-if="['DOWNLOADING', 'PROCESSING'].includes(row.status)"
                      class="task-progress-info"
                    >
                      <el-progress
                        :percentage="Math.round(row.progress * 100)"
                        :format="percentFormat"
                        :status="getProgressStatus(row.status)"
                      />
                      <div class="task-status-message">
                        {{ row.message || getDefaultProgressMessage(row) }}
                      </div>
                    </div>
                    <div v-else-if="row.status === 'COMPLETED'" class="task-completed">
                      <el-progress :percentage="100" status="success" />
                      <div class="task-status-message">下载完成</div>
                    </div>
                    <div v-else-if="row.status === 'FAILED'" class="task-failed">
                      <el-progress
                        :percentage="Math.round(row.progress * 100)"
                        status="exception"
                      />
                      <div class="task-status-message">{{ row.message || '下载失败' }}</div>
                    </div>
                    <div v-else-if="row.status === 'TERMINATED'" class="task-terminated">
                      <el-progress :percentage="Math.round(row.progress * 100)" status="warning" />
                      <div class="task-status-message">已终止</div>
                    </div>
                    <div v-else class="task-pending">
                      <el-progress :percentage="0" />
                      <div class="task-status-message">等待中</div>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="220" align="center" fixed="right">
                <template #default="{ row }">
                  <div class="task-actions">
                    <el-button-group v-if="!row.deleted">
                      <el-button
                        v-if="['PENDING', 'DOWNLOADING', 'PROCESSING'].includes(row.status)"
                        size="small"
                        type="warning"
                        @click="terminateTask(row.id)"
                        :loading="row.id === activeTaskId && actionType === 'terminate'"
                      >
                        终止
                      </el-button>
                      <el-button
                        v-if="['COMPLETED', 'FAILED', 'TERMINATED'].includes(row.status)"
                        size="small"
                        type="primary"
                        @click="redownloadTask(row.id)"
                        :loading="row.id === activeTaskId && actionType === 'redownload'"
                      >
                        重新下载
                      </el-button>
                      <el-button
                        size="small"
                        type="danger"
                        @click="deleteTask(row.id)"
                        :loading="row.id === activeTaskId && actionType === 'delete'"
                      >
                        删除
                      </el-button>
                    </el-button-group>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="task-error" v-if="taskStore.error">
            <el-alert :title="taskStore.error" type="error" :closable="false" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useTaskStore, useAuthStore } from '../store'
import api from '../api'
import type { TaskStatusEnum } from '../api'

const router = useRouter()
const taskStore = useTaskStore()
const authStore = useAuthStore()
const activeTaskId = ref<number | null>(null)
const actionType = ref<'terminate' | 'delete' | 'redownload' | null>(null)
const socketConnected = ref(false)

// 检查WebSocket连接状态
const checkSocketConnection = () => {
  socketConnected.value = api.WebSocketAPI.isConnected()
}

// 添加实时百分比格式化函数
const percentFormat = (percentage: number) => {
  return `${percentage}%`
}

// 刷新任务列表
const refreshTasks = async () => {
  await taskStore.fetchTasks()

  // 重新检查WebSocket连接状态
  checkSocketConnection()

  // 如果WebSocket未连接，尝试重连
  if (!socketConnected.value && authStore.isAuthenticated) {
    setupWebSocketConnection()
  }
}

const navigateToUpload = () => {
  router.push('/upload')
}

const terminateTask = async (taskId: number) => {
  activeTaskId.value = taskId
  actionType.value = 'terminate'

  try {
    await ElMessageBox.confirm('确定要终止此任务吗？该操作不可逆。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    const result = await taskStore.terminateTask(taskId)
    if (result) {
      ElMessage.success('任务已终止')
    }
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('终止任务失败')
    }
  } finally {
    activeTaskId.value = null
    actionType.value = null
  }
}

const deleteTask = async (taskId: number) => {
  activeTaskId.value = taskId
  actionType.value = 'delete'

  try {
    await ElMessageBox.confirm('确定要删除此任务吗？该操作不可逆。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    const result = await taskStore.deleteTask(taskId)
    if (result) {
      ElMessage.success('任务已删除')
    }
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除任务失败')
    }
  } finally {
    activeTaskId.value = null
    actionType.value = null
  }
}

const redownloadTask = async (taskId: number) => {
  activeTaskId.value = taskId
  actionType.value = 'redownload'

  try {
    const result = await taskStore.redownloadTask(taskId)
    if (result) {
      ElMessage.success('任务已重新添加到下载队列')
    }
  } catch (err) {
    ElMessage.error('重新下载失败')
  } finally {
    activeTaskId.value = null
    actionType.value = null
  }
}

const getStatusType = (status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'TERMINATED':
      return 'warning'
    case 'DOWNLOADING':
    case 'PROCESSING':
      return 'primary'
    default:
      return 'info'
  }
}

const getStatusText = (status: string): string => {
  switch (status) {
    case 'PENDING':
      return '等待中'
    case 'DOWNLOADING':
      return '下载中'
    case 'PROCESSING':
      return '处理中'
    case 'COMPLETED':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'TERMINATED':
      return '已终止'
    default:
      return status
  }
}

const getProgressStatus = (status: string): '' | 'success' | 'exception' | 'warning' => {
  switch (status) {
    case 'DOWNLOADING':
      return 'success'
    case 'PROCESSING':
      return ''
    case 'FAILED':
      return 'exception'
    case 'TERMINATED':
      return 'warning'
    default:
      return ''
  }
}

const getDefaultProgressMessage = (task) => {
  if (task.status === 'DOWNLOADING') {
    // 提取下载信息
    const downloadMatch = task.message?.match(/Downloading (\d+)\/(\d+) chapters/)
    if (downloadMatch && downloadMatch.length >= 3) {
      const current = parseInt(downloadMatch[1])
      const total = parseInt(downloadMatch[2])
      return `下载章节: ${current}/${total}`
    }
    return '正在下载章节...'
  } else if (task.status === 'PROCESSING') {
    return '正在处理小说数据...'
  }
  return '处理中...'
}

const formatDate = (dateStr: string | null): string => {
  if (!dateStr) return '-'

  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}

// 设置 WebSocket 连接和监听
const setupWebSocketConnection = () => {
  if (api.WebSocketAPI.isConnected()) {
    socketConnected.value = true
    return
  }

  // 设置 WebSocket 连接以获取任务更新
  taskStore.setupWebSocketListener()

  // 增加定时器检查WebSocket连接状态
  const checkInterval = setInterval(() => {
    const isConnected = api.WebSocketAPI.isConnected()
    if (isConnected !== socketConnected.value) {
      socketConnected.value = isConnected
    }
  }, 3000)

  // 组件卸载时清除定时器
  onUnmounted(() => {
    clearInterval(checkInterval)
  })
}

// 添加对任务列表的监听
watch(
  () => taskStore.tasks,
  () => {
    // 更新WebSocket连接状态
    checkSocketConnection()
  },
  { deep: true },
)

onMounted(() => {
  // 获取任务列表
  taskStore.fetchTasks()

  // 设置WebSocket连接
  if (authStore.isAuthenticated) {
    setupWebSocketConnection()
  }

  // 初始检查连接状态
  checkSocketConnection()
})
</script>

<style scoped>
.tasks-container {
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

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.task-details {
  padding: 10px;
}

.novel-info {
  display: flex;
  flex-direction: column;
}

.novel-title {
  color: #409eff;
  text-decoration: none;
  font-weight: bold;
}

.novel-title:hover {
  text-decoration: underline;
}

.novel-author {
  color: #606266;
  font-size: 13px;
  margin-top: 5px;
}

.task-actions {
  display: flex;
  justify-content: center;
}

.task-error {
  margin-top: 15px;
}

.current-task {
  font-weight: bold;
  background-color: #f5f7fa;
}

.task-progress-container {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.task-status-message {
  font-size: 12px;
  color: #606266;
  margin-top: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-progress-info,
.task-completed,
.task-failed,
.task-terminated,
.task-pending {
  width: 100%;
}
</style>
