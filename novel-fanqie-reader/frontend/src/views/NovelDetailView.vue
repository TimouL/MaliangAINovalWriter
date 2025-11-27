<template>
  <div class="novel-detail-container" v-loading="novelStore.isLoading">
    <el-page-header @back="goBack" />

    <div class="novel-info" v-if="novelStore.currentNovel">
      <div class="novel-basic-info">
        <div class="novel-cover">
          <el-image
            :src="getCoverUrl(novelStore.currentNovel.id)"
            fit="cover"
            :preview-src-list="[getCoverUrl(novelStore.currentNovel.id)]"
            fallback-src="https://via.placeholder.com/240x320/e6a23c/ffffff?text=封面"
          />
        </div>
        <div class="novel-meta">
          <h1 class="novel-title">{{ novelStore.currentNovel.title }}</h1>
          <div class="novel-author">
            <strong>作者：</strong> {{ novelStore.currentNovel.author || '未知' }}
          </div>
          <div class="novel-status">
            <strong>状态：</strong>
            <el-tag :type="getStatusTag(novelStore.currentNovel.status)">
              {{ novelStore.currentNovel.status || '未知' }}
            </el-tag>
          </div>
          <div class="novel-tags" v-if="novelStore.currentNovel.tags">
            <strong>标签：</strong>
            <div class="tags-list">
              <el-tag
                v-for="tag in novelStore.currentNovel.tags.split('|')"
                :key="tag"
                size="small"
                effect="plain"
                class="novel-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>
          <div class="novel-chapters-count">
            <strong>章节：</strong>
            <el-tooltip
              v-if="
                novelStore.currentNovel.chapters_in_db <
                (novelStore.currentNovel.total_chapters_source || 0)
              "
              effect="dark"
              content="正在爬取更多章节"
              placement="top"
            >
              <span>
                {{ novelStore.currentNovel.chapters_in_db }} /
                {{ novelStore.currentNovel.total_chapters_source || '?' }}
              </span>
              <el-icon class="is-loading"><loading /></el-icon>
            </el-tooltip>
            <span v-else>{{ novelStore.currentNovel.chapters_in_db }}</span>
          </div>
          <div class="novel-update-time">
            <strong>最近更新：</strong>
            {{ formatDate(novelStore.currentNovel.last_crawled_at) }}
          </div>
          <div class="novel-actions">
            <el-button type="primary" @click="scrollToChapters">开始阅读</el-button>
            <el-button type="info" :icon="RefreshRight" @click="refreshNovel">更新</el-button>
            <el-button
              type="success"
              :icon="Download"
              @click="downloadNovel"
              :loading="isDownloading"
            >
              下载
            </el-button>
            <el-button :icon="View" @click="viewWordCloud">查看词云</el-button>
          </div>
        </div>
      </div>

      <div class="novel-description">
        <h3>简介</h3>
        <p>{{ novelStore.currentNovel.description || '暂无简介' }}</p>
      </div>
    </div>

    <div class="novel-error" v-else-if="novelStore.error">
      <el-result icon="error" :title="novelStore.error" sub-title="无法加载小说信息">
        <template #extra>
          <el-button type="primary" @click="fetchNovelDetails">重试</el-button>
        </template>
      </el-result>
    </div>

    <div id="chapters-section" class="chapters-section" v-if="novelStore.currentNovel">
      <h2>目录</h2>
      <el-divider />

      <div v-loading="loadingChapters">
        <el-tabs v-model="chaptersTab">
          <el-tab-pane label="章节列表" name="list">
            <el-table
              :data="chapters"
              style="width: 100%"
              @row-click="openChapter"
              v-if="chapters.length > 0"
            >
              <el-table-column prop="index" label="章节" width="80" />
              <el-table-column prop="title" label="标题" min-width="200" />
              <el-table-column label="获取时间" width="180">
                <template #default="{ row }">
                  {{ formatDate(row.fetched_at) }}
                </template>
              </el-table-column>
              <el-table-column width="100">
                <template #default="{ row }">
                  <el-button type="primary" size="small" text @click.stop="openChapter(row)">
                    阅读
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="暂无章节" />
            <div class="chapters-pagination" v-if="totalChapters > 0">
              <el-pagination
                v-model:current-page="currentPage"
                :page-size="perPage"
                layout="total, prev, pager, next, jumper"
                :total="totalChapters"
                @current-change="handlePageChange"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="词云视图" name="wordcloud">
            <div class="wordcloud-container">
              <el-image
                v-if="wordCloudSrc"
                :src="wordCloudSrc"
                fit="contain"
                class="wordcloud-image"
              />
              <el-empty v-else description="词云加载中..." />
              <p class="wordcloud-note">词云会根据小说内容自动生成，展示出现频率较高的词语。</p>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <el-dialog v-model="wordCloudDialogVisible" title="词云视图" width="80%" center>
      <div class="wordcloud-dialog-content">
        <el-image v-if="wordCloudSrc" :src="wordCloudSrc" fit="contain" style="width: 100%" />
        <el-empty v-else description="正在加载词云..." />
      </div>
    </el-dialog>

    <!-- 下载选项对话框 -->
    <el-dialog v-model="downloadOptions.visible" title="下载选项" width="400px">
      <el-form label-position="top">
        <el-form-item label="文件格式">
          <el-select v-model="downloadOptions.format" style="width: 100%">
            <el-option value="epub" label="EPUB文件" />
            <el-option value="txt" label="文本文件 (TXT)" disabled />
            <el-option value="pdf" label="PDF文件" disabled />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="downloadOptions.includeImages">包含封面图片</el-checkbox>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="downloadOptions.visible = false">取消</el-button>
          <el-button type="primary" @click="downloadNovel">下载</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { RefreshRight, View, Download, Loading } from '@element-plus/icons-vue'
import { useNovelStore } from '../store'
import api from '../api'
import type { ChapterSummary, ErrorResponse } from '../api'

const route = useRoute()
const router = useRouter()
const novelStore = useNovelStore()

const novelId = computed(() => route.params.novelId as string)
const chapterId = computed(() => route.params.chapterId as string)
const nearbyChapters = ref<ChapterSummary[]>([])
const hasPrevChapter = ref(false)
const hasNextChapter = ref(false)
const loadingChapters = ref(false)
const chapters = ref<ChapterSummary[]>([])
const totalChapters = ref(0)
const currentPage = ref(1)
const perPage = ref(50)
const chaptersTab = ref<'list' | 'wordcloud'>('list')

// 词云图片 Object URL
const wordCloudSrc = ref<string>('')
const wordCloudDialogVisible = ref(false)

// 下载相关状态
const isDownloading = ref(false)
const downloadOptions = ref({
  visible: false,
  format: 'epub',
  includeImages: true,
})

const fetchNovelDetails = async () => {
  if (!novelId.value) return
  await novelStore.fetchNovelDetails(novelId.value)
  fetchChapters()
}

const fetchChapters = async () => {
  if (!novelId.value) return
  loadingChapters.value = true
  try {
    const response = await api.Novels.listChapters(novelId.value, currentPage.value, perPage.value)
    if ('error' in response) {
      ElMessage.error(response.error)
      return
    }
    chapters.value = response.items
    totalChapters.value = response.total
  } catch {
    ElMessage.error('获取章节列表失败')
  } finally {
    loadingChapters.value = false
  }
}

const handlePageChange = () => {
  fetchChapters()
}

const openChapter = (chapter: ChapterSummary) => {
  router.push(`/novel/${novelId.value}/chapter/${chapter.id}`)
}

const goBack = () => router.back()

const scrollToChapters = () => {
  document.getElementById('chapters-section')?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

const refreshNovel = async () => {
  if (!novelId.value) return
  try {
    const response = await novelStore.addNovel(novelId.value)
    if (response) {
      ElMessage.success('已添加到更新队列')
      if (response.task_id) ElMessage.info('后台正在更新小说，请稍后刷新页面查看结果')
    }
  } catch {
    ElMessage.error('更新失败')
  }
}

/**
 * 下载小说文件
 */
const downloadNovel = async () => {
  if (!novelId.value) return

  isDownloading.value = true

  try {
    const response = await api.Novels.fetchNovelBlob(novelId.value)

    // 处理错误响应
    if ('error' in response) {
      ElMessage.error(response.error)
      return
    }

    // 创建下载链接
    const blob = response as Blob
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')

    // 设置链接属性
    link.href = url
    link.download = `${novelStore.currentNovel?.title || 'novel'}.epub`
    document.body.appendChild(link)

    // 触发下载
    link.click()

    // 清理
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    ElMessage.success('下载开始')
    downloadOptions.value.visible = false
  } catch (err) {
    ElMessage.error('下载失败，请重试')
    console.error('Download failed:', err)
  } finally {
    isDownloading.value = false
  }
}

// 显示下载选项
const showDownloadOptions = () => {
  downloadOptions.value.visible = true
}

// 加载词云图
async function loadWordCloud() {
  if (!novelId.value) return
  wordCloudSrc.value = ''
  try {
    const response = await api.Stats.fetchWordCloudBlob(novelId.value)
    if (typeof response === 'string') {
      wordCloudSrc.value = response
    } else if ('error' in response) {
      ElMessage.error(response.error)
    }
  } catch {
    ElMessage.error('词云加载失败')
  }
}

watch(chaptersTab, async (tab) => {
  if (tab === 'wordcloud' && !wordCloudSrc.value) {
    await loadWordCloud()
  }
})

function viewWordCloud() {
  wordCloudDialogVisible.value = true
  if (!wordCloudSrc.value) loadWordCloud()
}

function getCoverUrl(id: string): string {
  return api.Novels.getCoverUrl(id)
}

function getStatusTag(status: string | null): '' | 'success' | 'warning' | 'info' | 'danger' {
  if (!status) return ''
  if (status.includes('完结')) return 'success'
  if (status.includes('连载')) return 'primary'
  return 'info'
}

function formatDate(dateStr: string | null): string {
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

watch(
  () => route.params.id,
  (newId) => {
    if (newId) {
      currentPage.value = 1
      fetchNovelDetails()
    }
  },
)

onMounted(() => {
  fetchNovelDetails()
})
</script>

<style scoped>
.novel-detail-container {
  padding-bottom: 40px;
}
.novel-info {
  margin-top: 30px;
}
.novel-basic-info {
  display: flex;
  gap: 30px;
  margin-bottom: 30px;
}
.novel-cover {
  flex-shrink: 0;
  width: 240px;
  height: 320px;
  overflow: hidden;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}
.novel-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.novel-title {
  margin-top: 0;
  margin-bottom: 10px;
  font-size: 28px;
}
.novel-tags {
  display: flex;
  align-items: flex-start;
}
.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-left: 5px;
}
.novel-tag {
  margin-right: 0;
}
.novel-actions {
  margin-top: auto;
  display: flex;
  gap: 10px;
  flex-wrap: wrap; /* 允许按钮在小屏幕上换行 */
}
.novel-description {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 30px;
}
.novel-description h3 {
  margin-top: 0;
  margin-bottom: 10px;
}
.novel-description p {
  margin: 0;
  line-height: 1.6;
  white-space: pre-line;
}
.chapters-section {
  margin-top: 40px;
}
.chapters-pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
.wordcloud-container {
  text-align: center;
  padding: 20px;
}
.wordcloud-image {
  max-width: 100%;
  max-height: 500px;
}
.wordcloud-note {
  margin-top: 20px;
  color: #909399;
  font-size: 14px;
}
.wordcloud-dialog-content {
  display: flex;
  justify-content: center;
}
.novel-error {
  margin-top: 40px;
}
</style>
