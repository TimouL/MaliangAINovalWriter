<!-- views/ChapterView.vue -->
<template>
  <div class="chapter-container" v-loading="novelStore.isLoading">
    <div class="chapter-header">
      <el-page-header @back="goBack" />

      <div class="chapter-nav">
        <el-button
          :disabled="!hasPrevChapter"
          @click="navigateToChapter('prev')"
          :icon="ArrowLeft"
          type="primary"
          plain
        >
          上一章
        </el-button>

        <el-dropdown @command="handleCommand">
          <el-button type="primary">
            目录
            <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu class="chapter-dropdown-menu">
              <el-dropdown-item
                v-for="chapter in nearbyChapters"
                :key="chapter.id"
                :command="chapter.id"
                :class="{ 'current-chapter': chapter.id === chapterId }"
              >
                {{ chapter.title }}
              </el-dropdown-item>
              <el-dropdown-item divided command="all">查看全部章节</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-button
          :disabled="!hasNextChapter"
          @click="navigateToChapter('next')"
          :icon="ArrowRight"
          type="primary"
          plain
        >
          下一章
        </el-button>
      </div>
    </div>

    <div class="chapter-content-wrapper" v-if="novelStore.currentChapter">
      <div class="chapter-info">
        <h1 class="chapter-title">{{ novelStore.currentChapter.title }}</h1>

        <div class="reading-settings">
          <el-button-group>
            <el-tooltip content="减小字体" placement="top">
              <el-button @click="decreaseFontSize" :icon="Minus" plain />
            </el-tooltip>
            <el-tooltip content="增大字体" placement="top">
              <el-button @click="increaseFontSize" :icon="Plus" plain />
            </el-tooltip>
          </el-button-group>

          <el-tooltip content="阅读模式" placement="top">
            <el-button
              @click="toggleReadingMode"
              :icon="readingMode ? Moon : Sunny"
              plain
              type="default"
            />
          </el-tooltip>
        </div>
      </div>

      <!-- 使用 v-html 渲染后端返回的 HTML -->
      <div
        class="chapter-content"
        :class="{ 'dark-mode': readingMode }"
        :style="{ fontSize: fontSize + 'px' }"
        v-html="novelStore.currentChapter.content"
      ></div>

      <div class="chapter-footer">
        <div class="chapter-nav">
          <el-button
            :disabled="!hasPrevChapter"
            @click="navigateToChapter('prev')"
            :icon="ArrowLeft"
            type="primary"
            plain
          >
            上一章
          </el-button>

          <el-button @click="router.push(`/novel/${novelId}`)" type="primary"> 返回目录 </el-button>

          <el-button
            :disabled="!hasNextChapter"
            @click="navigateToChapter('next')"
            :icon="ArrowRight"
            type="primary"
            plain
          >
            下一章
          </el-button>
        </div>
      </div>
    </div>

    <div class="chapter-error" v-else-if="novelStore.error">
      <el-result icon="error" :title="novelStore.error" sub-title="无法加载章节内容">
        <template #extra>
          <el-button type="primary" @click="fetchChapterContent">重试</el-button>
          <el-button @click="router.push(`/novel/${novelId}`)">返回小说页面</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, ArrowDown, Minus, Plus, Moon, Sunny } from '@element-plus/icons-vue'
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
const fontSize = ref(18)
const readingMode = ref(false)

const fetchChapterContent = async () => {
  if (!novelId.value || !chapterId.value) return

  await novelStore.fetchChapterContent(novelId.value, chapterId.value)
  fetchNearbyChapters()
}

const fetchNearbyChapters = async () => {
  if (!novelId.value || !chapterId.value) return

  try {
    const response = await api.Novels.listChapters(novelId.value, 1, 10)
    if ('error' in response) {
      ElMessage.error(response.error)
      return
    }
    nearbyChapters.value = response.items
    const currentIndex = nearbyChapters.value.findIndex((ch) => ch.id === chapterId.value)
    hasPrevChapter.value = currentIndex > 0
    hasNextChapter.value = currentIndex < nearbyChapters.value.length - 1
  } catch {
    ElMessage.error('获取章节列表失败')
  }
}

const navigateToChapter = (direction: 'prev' | 'next') => {
  if (!novelId.value) return
  const currentIndex = nearbyChapters.value.findIndex((ch) => ch.id === chapterId.value)
  if (currentIndex === -1) return

  let target: ChapterSummary | undefined
  if (direction === 'prev' && currentIndex > 0) {
    target = nearbyChapters.value[currentIndex - 1]
  } else if (direction === 'next' && currentIndex < nearbyChapters.value.length - 1) {
    target = nearbyChapters.value[currentIndex + 1]
  }
  if (target) {
    router.push(`/novel/${novelId.value}/chapter/${target.id}`)
  }
}

const handleCommand = (command: string | number) => {
  if (command === 'all') {
    router.push(`/novel/${novelId.value}`)
  } else {
    router.push(`/novel/${novelId.value}/chapter/${command}`)
  }
}

const goBack = () => {
  router.push(`/novel/${novelId.value}`)
}

const increaseFontSize = () => {
  if (fontSize.value < 30) {
    fontSize.value += 2
    saveSettings()
  }
}

const decreaseFontSize = () => {
  if (fontSize.value > 14) {
    fontSize.value -= 2
    saveSettings()
  }
}

const toggleReadingMode = () => {
  readingMode.value = !readingMode.value
  saveSettings()
}

const saveSettings = () => {
  localStorage.setItem(
    'readingSettings',
    JSON.stringify({
      fontSize: fontSize.value,
      readingMode: readingMode.value,
    }),
  )
}

const loadSettings = () => {
  const settings = localStorage.getItem('readingSettings')
  if (settings) {
    try {
      const parsed = JSON.parse(settings)
      fontSize.value = parsed.fontSize || 18
      readingMode.value = parsed.readingMode || false
    } catch {
      // 解析错误，保持默认
    }
  }
}

watch(
  () => [route.params.novelId, route.params.chapterId],
  ([newNovelId, newChapterId]) => {
    if (newNovelId && newChapterId) {
      fetchChapterContent()
    }
  },
)

onMounted(() => {
  loadSettings()
  fetchChapterContent()
})
</script>

<style scoped>
.chapter-container {
  max-width: 800px;
  margin: 0 auto;
  padding-bottom: 60px;
}

.chapter-header {
  margin-bottom: 30px;
}

.chapter-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
}

.chapter-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.chapter-title {
  margin: 0;
  font-size: 24px;
}

.chapter-content-wrapper {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 30px;
  margin-bottom: 30px;
}

.chapter-content {
  padding: 20px 0;
  line-height: 1.8;
  font-family: 'Noto Serif SC', serif, 'Microsoft YaHei', '微软雅黑';
  text-align: justify;
}

.chapter-content p {
  margin-bottom: 1em;
  text-indent: 2em;
}

.chapter-content.dark-mode {
  background-color: #2b2b2b;
  color: #e0e0e0;
}

.chapter-footer {
  margin-top: 50px;
}

.chapter-dropdown-menu {
  min-width: 200px;
  max-height: 400px;
  overflow-y: auto;
}

.current-chapter {
  color: #409eff;
  font-weight: bold;
}

.chapter-error {
  margin-top: 40px;
}

/* 阅读模式样式 */
.dark-mode {
  background-color: #2b2b2b;
  color: #e0e0e0;
}
</style>
