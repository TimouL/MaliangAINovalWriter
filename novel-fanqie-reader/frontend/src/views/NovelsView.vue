<!-- views/NovelsView.vue -->
<template>
  <div class="novels-container">
    <h2 class="page-title">小说列表</h2>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-card>
          <div class="novels-header">
            <div class="novels-filters">
              <el-select v-model="perPage" placeholder="每页显示" @change="handlePerPageChange">
                <el-option :value="10" label="10条/页" />
                <el-option :value="20" label="20条/页" />
                <el-option :value="50" label="50条/页" />
              </el-select>
            </div>
            <el-button type="primary" @click="router.push('/upload')">添加小说</el-button>
          </div>

          <el-table
            v-loading="novelStore.isLoading"
            :data="novelStore.novels"
            style="width: 100%"
            @row-click="handleRowClick"
          >
            <el-table-column width="80">
              <template #default="{ row }">
                <div class="novel-cover">
                  <el-image
                    :src="row.cover_image_url || '/api/novels/' + row.id + '/cover'"
                    fit="cover"
                    :preview-src-list="row.cover_image_url ? [row.cover_image_url] : []"
                    fallback-src="https://via.placeholder.com/60x80/e6a23c/ffffff?text=封面"
                  />
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="{ row }">
                <router-link :to="'/novel/' + String(row.id)" class="novel-title">
                  {{ row.title }}
                </router-link>
              </template>
            </el-table-column>

            <el-table-column prop="author" label="作者" min-width="120" />

            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTag(row.status)">
                  {{ row.status || '未知' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="total_chapters" label="章节数" width="100" />

            <el-table-column label="标签" min-width="150">
              <template #default="{ row }">
                <div class="novel-tags">
                  <template v-if="row.tags">
                    <el-tag
                      v-for="tag in row.tags.split('|')"
                      :key="tag"
                      size="small"
                      effect="plain"
                      class="novel-tag"
                    >
                      {{ tag }}
                    </el-tag>
                  </template>
                  <span v-else>-</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="最近更新" width="180">
              <template #default="{ row }">
                {{ formatDate(row.last_crawled_at) }}
              </template>
            </el-table-column>
          </el-table>

          <div class="novels-pagination">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="perPage"
              layout="total, prev, pager, next, jumper"
              :total="novelStore.pagination.total"
              @current-change="handlePageChange"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNovelStore } from '../store'
import type { NovelSummary } from '../api'

const router = useRouter()
const novelStore = useNovelStore()

const currentPage = ref(1)
const perPage = ref(10)

// 获取小说列表
const fetchNovels = async () => {
  await novelStore.fetchNovels(currentPage.value, perPage.value)
}

// 处理页码变化
const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchNovels()
}

// 处理每页条数变化
const handlePerPageChange = () => {
  currentPage.value = 1
  fetchNovels()
}

// 处理行点击
const handleRowClick = (row: NovelSummary) => {
  router.push(`/novel/${String(row.id)}`)
}

// 获取状态标签类型
const getStatusTag = (status: string | null): '' | 'success' | 'warning' | 'info' | 'danger' => {
  if (!status) return ''

  if (status.includes('完结')) return 'success'
  if (status.includes('连载')) return 'primary'
  return 'info'
}

// 格式化日期
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
  } catch (e) {
    return dateStr
  }
}

// 监听页面变化
watch([currentPage, perPage], () => {
  fetchNovels()
})

onMounted(() => {
  fetchNovels()
})
</script>

<style scoped>
.novels-container {
  padding-bottom: 20px;
}

.page-title {
  margin-bottom: 20px;
}

.novels-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.novels-filters {
  display: flex;
  gap: 10px;
}

.novels-pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.novel-cover {
  width: 60px;
  height: 80px;
  overflow: hidden;
  border-radius: 4px;
}

.novel-title {
  color: #409eff;
  text-decoration: none;
  font-weight: bold;
}

.novel-title:hover {
  text-decoration: underline;
}

.novel-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.novel-tag {
  margin-right: 0;
}
</style>
