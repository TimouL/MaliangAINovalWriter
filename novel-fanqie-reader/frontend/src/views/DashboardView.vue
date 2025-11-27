<!-- views/DashboardView.vue -->
<template>
  <div class="dashboard-container">
    <h2 class="page-title">数据大屏</h2>

    <!-- 概览卡片 - 优先加载 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>概览</h3>
              <el-button
                type="primary"
                :icon="Refresh"
                circle
                @click="refreshData"
                :loading="refreshing"
              />
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="8">
              <div class="stat-card" v-loading="overviewLoading">
                <el-statistic title="小说总数" :value="stats.totalNovels">
                  <template #icon>
                    <el-icon><Reading /></el-icon>
                  </template>
                </el-statistic>
              </div>
            </el-col>

            <el-col :span="8">
              <div class="stat-card" v-loading="overviewLoading">
                <el-statistic title="本月新增" :value="stats.monthlyNovels">
                  <template #icon>
                    <el-icon><Plus /></el-icon>
                  </template>
                </el-statistic>
              </div>
            </el-col>

            <el-col :span="8">
              <div class="stat-card" v-loading="overviewLoading">
                <el-statistic title="今日新增" :value="stats.todayNovels">
                  <template #icon>
                    <el-icon><Calendar /></el-icon>
                  </template>
                </el-statistic>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- 小说状态分布和热门标签 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>小说状态分布</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="statusChartLoading">
            <div id="status-chart" class="echarts-container" ref="statusChartRef"></div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>热门标签分析</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="tagsChartLoading">
            <div id="tags-chart" class="echarts-container" ref="tagsChartRef"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 章节数量分布和热门作者 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>章节数量分布</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="chaptersChartLoading">
            <div id="chapters-chart" class="echarts-container" ref="chaptersChartRef"></div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>热门作者排行</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="authorsChartLoading">
            <div id="authors-chart" class="echarts-container" ref="authorsChartRef"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增: 更新频率分析和标签关联分析 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>更新频率分析</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="updateFrequencyChartLoading">
            <div
              id="update-frequency-chart"
              class="echarts-container"
              ref="updateFrequencyChartRef"
            ></div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="24" :lg="12">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>标签关联分析</h3>
            </div>
          </template>

          <div class="chart-container" v-loading="tagRelationChartLoading">
            <div id="tag-relation-chart" class="echarts-container" ref="tagRelationChartRef"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增: 词云聚合展示 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>热门小说词云</h3>
            </div>
          </template>

          <div class="wordcloud-panel" v-loading="wordCloudPanelLoading">
            <div v-if="wordClouds.length > 0" class="wordcloud-grid">
              <div v-for="cloud in wordClouds" :key="cloud.novelId" class="wordcloud-item">
                <h4>{{ cloud.novelTitle }}</h4>
                <el-image
                  :src="cloud.wordCloudUrl"
                  fit="contain"
                  class="wordcloud-image"
                  :preview-src-list="[cloud.wordCloudUrl]"
                />
              </div>
            </div>
            <el-empty v-else description="暂无词云数据" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>小说添加趋势</h3>
              <div class="chart-actions">
                <el-radio-group v-model="timeRange" size="small" @change="updateTimeRange">
                  <el-radio-button value="week">最近一周</el-radio-button>
                  <el-radio-button value="month">最近一月</el-radio-button>
                  <el-radio-button value="year">最近一年</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>

          <div class="chart-container" v-loading="trendChartLoading">
            <div id="trend-chart" class="echarts-container" ref="trendChartRef"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热门小说 - 延迟加载 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover" class="data-card">
          <template #header>
            <div class="card-header">
              <h3>热门小说</h3>
            </div>
          </template>

          <div v-loading="topNovelsLoading">
            <el-table :data="topNovels" style="width: 100%" @row-click="handleNovelClick">
              <el-table-column type="index" width="50" />

              <el-table-column width="80">
                <template #default="{ row }">
                  <div class="novel-cover-small">
                    <el-image
                      :src="getCoverUrl(row.id)"
                      fit="cover"
                      fallback-src="https://via.placeholder.com/40x60/e6a23c/ffffff?text=封面"
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

              <el-table-column label="标签" min-width="150">
                <template #default="{ row }">
                  <div class="novel-tags">
                    <template v-if="row.tags">
                      <el-tag
                        v-for="tag in row.tags.split('|').slice(0, 3)"
                        :key="tag"
                        size="small"
                        effect="plain"
                        class="novel-tag"
                      >
                        {{ tag }}
                      </el-tag>
                      <span v-if="row.tags.split('|').length > 3">...</span>
                    </template>
                    <span v-else>-</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="total_chapters" label="章节数" width="100" />

              <el-table-column label="最近更新" width="180">
                <template #default="{ row }">
                  {{ formatDate(row.last_crawled_at) }}
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Reading, Plus, Calendar } from '@element-plus/icons-vue'
import { useStatsStore } from '../store'
import type { NovelSummary } from '../api'
import api from '../api'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart, GraphChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 注册必要的 ECharts 组件
echarts.use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  LineChart,
  PieChart,
  BarChart,
  GraphChart,
  CanvasRenderer,
])

const router = useRouter()
const statsStore = useStatsStore()

// 细分加载状态
const refreshing = ref(false)
const overviewLoading = ref(false)
const trendChartLoading = ref(false)
const genreChartLoading = ref(false)
const topNovelsLoading = ref(false)

// 现有图表的加载状态
const statusChartLoading = ref(false)
const tagsChartLoading = ref(false)
const chaptersChartLoading = ref(false)
const authorsChartLoading = ref(false)

// 新增图表的加载状态
const updateFrequencyChartLoading = ref(false)
const tagRelationChartLoading = ref(false)
const wordCloudPanelLoading = ref(false)

const timeRange = ref('month')
const stats = ref({
  totalNovels: 0,
  monthlyNovels: 0,
  todayNovels: 0,
})
const topNovels = ref<NovelSummary[]>([])

// 词云数据
const wordClouds = ref<{ novelId: string; novelTitle: string; wordCloudUrl: string }[]>([])

// ECharts 引用和实例
const trendChartRef = ref<HTMLElement | null>(null)
const genreChartRef = ref<HTMLElement | null>(null)

// 现有图表引用
const statusChartRef = ref<HTMLElement | null>(null)
const tagsChartRef = ref<HTMLElement | null>(null)
const chaptersChartRef = ref<HTMLElement | null>(null)
const authorsChartRef = ref<HTMLElement | null>(null)

// 新增图表引用
const updateFrequencyChartRef = ref<HTMLElement | null>(null)
const tagRelationChartRef = ref<HTMLElement | null>(null)

let trendChart: echarts.ECharts | null = null
let genreChart: echarts.ECharts | null = null

// 现有图表实例
let statusChart: echarts.ECharts | null = null
let tagsChart: echarts.ECharts | null = null
let chaptersChart: echarts.ECharts | null = null
let authorsChart: echarts.ECharts | null = null

// 新增图表实例
let updateFrequencyChart: echarts.ECharts | null = null
let tagRelationChart: echarts.ECharts | null = null

// 使用防抖处理窗口调整
let resizeDebounceTimer: number | null = null
// 缓存数据
const uploadStatsCache = ref<any[]>([])
const genreStatsCache = ref<any[]>([])

// 分步加载数据
const fetchData = async () => {
  // 1. 首先加载概览数据（优先级最高）
  await fetchOverviewData()

  // 2. 然后加载热门小说
  fetchTopNovels()

  // 3. 最后初始化图表（最耗资源的部分）
  nextTick(() => {
    // 在下一个渲染周期再加载图表，避免阻塞页面
    setTimeout(() => {
      initCharts()
    }, 100)
  })
}

// 获取概览数据
const fetchOverviewData = async () => {
  overviewLoading.value = true

  try {
    await statsStore.fetchUploadStats()
    uploadStatsCache.value = [...statsStore.uploadStats]

    // 计算统计数据
    calculateStats()
  } catch (err) {
    ElMessage.error('获取概览数据失败')
    console.error('获取概览数据失败:', err)
  } finally {
    overviewLoading.value = false
  }
}

// 获取图表数据
const fetchChartData = async () => {
  trendChartLoading.value = true
  genreChartLoading.value = true

  try {
    if (genreStatsCache.value.length === 0) {
      await statsStore.fetchGenreStats()
      genreStatsCache.value = [...statsStore.genreStats]
    }
  } catch (err) {
    ElMessage.error('获取图表数据失败')
    console.error('获取图表数据失败:', err)
  } finally {
    trendChartLoading.value = false
    genreChartLoading.value = false
  }
}

// 获取热门小说
const fetchTopNovels = async () => {
  topNovelsLoading.value = true

  try {
    const response = await api.Novels.list(1, 10)

    if ('error' in response) {
      ElMessage.error(response.error)
      return
    }

    topNovels.value = response.items
  } catch (err) {
    ElMessage.error('获取热门小说失败')
    console.error('获取热门小说失败:', err)
  } finally {
    topNovelsLoading.value = false
  }
}

// 刷新数据 - 按需刷新各个部分
const refreshData = async () => {
  refreshing.value = true

  try {
    // 先刷新概览数据
    await fetchOverviewData()

    // 然后刷新图表数据
    await fetchChartData()

    // 最后刷新热门小说
    await fetchTopNovels()

    // 更新图表
    updateCharts()

    // 更新词云数据
    initWordCloudPanel()

    ElMessage.success('数据已更新')
  } catch (err) {
    ElMessage.error('刷新数据失败')
    console.error('刷新数据失败:', err)
  } finally {
    refreshing.value = false
  }
}

// 优化计算统计数据
const calculateStats = () => {
  const uploadStats =
    uploadStatsCache.value.length > 0 ? uploadStatsCache.value : statsStore.uploadStats

  if (uploadStats.length === 0) {
    stats.value = {
      totalNovels: 0,
      monthlyNovels: 0,
      todayNovels: 0,
    }
    return
  }

  // 计算总小说数
  const total = uploadStats.reduce((sum, item) => sum + item.count, 0)

  // 获取当前日期
  const now = new Date()
  const today = now.toISOString().split('T')[0]

  // 计算今日新增
  const todayStats = uploadStats.find((item) => item.date === today)
  const todayCount = todayStats ? todayStats.count : 0

  // 计算本月新增
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0]
  const monthlyCount = uploadStats
    .filter((item) => item.date >= monthStart)
    .reduce((sum, item) => sum + item.count, 0)

  stats.value = {
    totalNovels: total,
    monthlyNovels: monthlyCount,
    todayNovels: todayCount,
  }
}

// 延迟初始化图表
const initCharts = async () => {
  // 先请求图表数据
  await fetchChartData()

  // 延迟初始化，分批处理图表
  setTimeout(() => {
    // 第一批: 原有基础图表
    initTrendChart()
    initStatusChart()

    // 延迟初始化新增的图表，分批次加载以降低性能压力
    setTimeout(() => {
      // 第二批: 标签和章节图表
      initTagsChart()
      initChaptersChart()

      setTimeout(() => {
        // 第三批: 作者和更新频率图表
        initAuthorsChart()
        initUpdateFrequencyChart()

        setTimeout(() => {
          // 第四批: 标签关联和词云
          initTagRelationChart()
          initWordCloudPanel()
        }, 100)
      }, 100)
    }, 100)
  }, 100)
}

// 初始化趋势图
const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChartLoading.value = true

  try {
    // 检查并销毁现有实例
    if (trendChart) {
      trendChart.dispose()
    }

    trendChart = echarts.init(trendChartRef.value)

    // 根据时间范围过滤数据
    const filteredData = filterDataByTimeRange(timeRange.value)

    // 设置图表选项
    const option = {
      title: {
        text: '小说添加趋势',
        left: 'center',
      },
      tooltip: {
        trigger: 'axis',
        formatter: '{b}: {c} 本',
      },
      xAxis: {
        type: 'category',
        data: filteredData.map((item) => item.date),
        axisLabel: {
          rotate: timeRange.value === 'year' ? 45 : 0,
        },
      },
      yAxis: {
        type: 'value',
        name: '小说数量',
        minInterval: 1,
      },
      series: [
        {
          name: '新增小说',
          type: 'line',
          data: filteredData.map((item) => item.count),
          smooth: true,
          areaStyle: {
            opacity: 0.3,
          },
          itemStyle: {
            color: '#409eff',
          },
        },
      ],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
      },
    }

    // 设置图表
    trendChart.setOption(option)
  } catch (err) {
    console.error('初始化趋势图失败:', err)
    ElMessage.error('初始化趋势图失败')
  } finally {
    trendChartLoading.value = false
  }
}

// 初始化小说状态分布图
const initStatusChart = () => {
  if (!statusChartRef.value) return

  statusChartLoading.value = true

  try {
    if (statusChart) {
      statusChart.dispose()
    }

    statusChart = echarts.init(statusChartRef.value)

    // 从存储中获取所有小说数据，分析状态分布
    // 这里使用Novels API获取小说列表，然后分析状态
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 统计不同状态的小说数量
        const statusMap: Record<string, number> = {}
        response.items.forEach((novel) => {
          const status = novel.status || '未知'
          statusMap[status] = (statusMap[status] || 0) + 1
        })

        // 转换为图表数据
        const statusData = Object.keys(statusMap).map((status) => ({
          name: status,
          value: statusMap[status],
        }))

        // 设置图表选项
        const option = {
          title: {
            text: '小说状态分布',
            left: 'center',
          },
          tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} 本 ({d}%)',
          },
          legend: {
            orient: 'vertical',
            left: 'left',
            top: 'center',
          },
          series: [
            {
              name: '小说状态',
              type: 'pie',
              radius: '55%',
              center: ['50%', '60%'],
              data: statusData,
              emphasis: {
                itemStyle: {
                  shadowBlur: 10,
                  shadowOffsetX: 0,
                  shadowColor: 'rgba(0, 0, 0, 0.5)',
                },
              },
            },
          ],
        }

        statusChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取小说状态数据失败:', err)
      })
      .finally(() => {
        statusChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化小说状态分布图失败:', err)
    ElMessage.error('初始化小说状态分布图失败')
    statusChartLoading.value = false
  }
}

// 初始化热门标签分析图
const initTagsChart = () => {
  if (!tagsChartRef.value) return

  tagsChartLoading.value = true

  try {
    if (tagsChart) {
      tagsChart.dispose()
    }

    tagsChart = echarts.init(tagsChartRef.value)

    // 从存储中获取所有小说数据，分析标签分布
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 统计不同标签的出现频率
        const tagMap: Record<string, number> = {}
        response.items.forEach((novel) => {
          if (novel.tags) {
            const tags = novel.tags.split('|')
            tags.forEach((tag) => {
              const trimmedTag = tag.trim()
              if (trimmedTag) {
                tagMap[trimmedTag] = (tagMap[trimmedTag] || 0) + 1
              }
            })
          }
        })

        // 转换为图表数据并排序，取前10个标签
        const tagsData = Object.keys(tagMap)
          .map((tag) => ({
            name: tag,
            value: tagMap[tag],
          }))
          .sort((a, b) => b.value - a.value)
          .slice(0, 10)

        // 设置图表选项
        const option = {
          title: {
            text: '热门标签分析',
            left: 'center',
          },
          tooltip: {
            trigger: 'axis',
            axisPointer: {
              type: 'shadow',
            },
            formatter: '{b}: {c} 本',
          },
          grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true,
          },
          xAxis: {
            type: 'value',
            boundaryGap: [0, 0.01],
          },
          yAxis: {
            type: 'category',
            data: tagsData.map((item) => item.name),
            axisTick: { alignWithLabel: true },
          },
          series: [
            {
              name: '小说数量',
              type: 'bar',
              data: tagsData.map((item) => item.value),
              itemStyle: {
                color: '#82ca9d',
              },
            },
          ],
        }

        tagsChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取标签数据失败:', err)
      })
      .finally(() => {
        tagsChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化热门标签分析图失败:', err)
    ElMessage.error('初始化热门标签分析图失败')
    tagsChartLoading.value = false
  }
}

// 初始化章节分布图
const initChaptersChart = () => {
  if (!chaptersChartRef.value) return

  chaptersChartLoading.value = true

  try {
    if (chaptersChart) {
      chaptersChart.dispose()
    }

    chaptersChart = echarts.init(chaptersChartRef.value)

    // 从存储中获取所有小说数据，分析章节数分布
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 定义章节数区间
        const ranges = [
          { min: 0, max: 50, label: '0-50章' },
          { min: 51, max: 100, label: '51-100章' },
          { min: 101, max: 200, label: '101-200章' },
          { min: 201, max: 500, label: '201-500章' },
          { min: 501, max: 1000, label: '501-1000章' },
          { min: 1001, max: Infinity, label: '1000章以上' },
        ]

        // 初始化计数
        const distribution = ranges.map((range) => ({
          name: range.label,
          value: 0,
        }))

        // 统计各区间的小说数量
        response.items.forEach((novel) => {
          const chapterCount = novel.total_chapters || 0
          const rangeIndex = ranges.findIndex(
            (range) => chapterCount >= range.min && chapterCount <= range.max,
          )

          if (rangeIndex !== -1) {
            distribution[rangeIndex].value++
          }
        })

        // 设置图表选项
        const option = {
          title: {
            text: '章节数量分布',
            left: 'center',
          },
          tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} 本',
          },
          xAxis: {
            type: 'category',
            data: distribution.map((item) => item.name),
            axisTick: { alignWithLabel: true },
          },
          yAxis: {
            type: 'value',
          },
          series: [
            {
              name: '小说数量',
              type: 'bar',
              data: distribution.map((item) => item.value),
              itemStyle: {
                color: '#8884d8',
              },
            },
          ],
        }

        chaptersChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取章节分布数据失败:', err)
      })
      .finally(() => {
        chaptersChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化章节分布图失败:', err)
    ElMessage.error('初始化章节分布图失败')
    chaptersChartLoading.value = false
  }
}

// 初始化热门作者排行图
const initAuthorsChart = () => {
  if (!authorsChartRef.value) return

  authorsChartLoading.value = true

  try {
    if (authorsChart) {
      authorsChart.dispose()
    }

    authorsChart = echarts.init(authorsChartRef.value)

    // 从存储中获取所有小说数据，分析作者作品数量
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 统计不同作者的作品数量
        const authorMap: Record<string, number> = {}
        response.items.forEach((novel) => {
          if (novel.author) {
            authorMap[novel.author] = (authorMap[novel.author] || 0) + 1
          }
        })

        // 转换为图表数据并排序，取前10个作者
        const authorsData = Object.keys(authorMap)
          .map((author) => ({
            name: author,
            value: authorMap[author],
          }))
          .sort((a, b) => b.value - a.value)
          .slice(0, 10)

        // 设置图表选项
        const option = {
          title: {
            text: '热门作者排行',
            left: 'center',
          },
          tooltip: {
            trigger: 'axis',
            axisPointer: {
              type: 'shadow',
            },
            formatter: '{b}: {c} 本',
          },
          grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true,
          },
          xAxis: {
            type: 'value',
            boundaryGap: [0, 0.01],
          },
          yAxis: {
            type: 'category',
            data: authorsData.map((item) => item.name),
            axisTick: { alignWithLabel: true },
          },
          series: [
            {
              name: '作品数量',
              type: 'bar',
              data: authorsData.map((item) => item.value),
              itemStyle: {
                color: '#ff7300',
              },
            },
          ],
        }

        authorsChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取作者数据失败:', err)
      })
      .finally(() => {
        authorsChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化热门作者排行图失败:', err)
    ElMessage.error('初始化热门作者排行图失败')
    authorsChartLoading.value = false
  }
}

// 新增: 初始化更新频率分析图
const initUpdateFrequencyChart = () => {
  if (!updateFrequencyChartRef.value) return

  updateFrequencyChartLoading.value = true

  try {
    if (updateFrequencyChart) {
      updateFrequencyChart.dispose()
    }

    updateFrequencyChart = echarts.init(updateFrequencyChartRef.value)

    // 使用Novels.list获取小说列表
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 分析最近更新时间，按照天数范围分组
        const now = new Date()
        const ranges = [
          { max: 1, label: '今天' },
          { min: 1, max: 7, label: '一周内' },
          { min: 7, max: 30, label: '一月内' },
          { min: 30, max: 90, label: '三月内' },
          { min: 90, max: 180, label: '半年内' },
          { min: 180, max: Infinity, label: '半年以上' },
        ]

        // 初始化计数
        const distribution = ranges.map((range) => ({
          name: range.label,
          value: 0,
        }))

        // 统计各区间的小说数量
        response.items.forEach((novel) => {
          if (novel.last_crawled_at) {
            const updateDate = new Date(novel.last_crawled_at)
            const daysDiff = Math.floor(
              (now.getTime() - updateDate.getTime()) / (1000 * 60 * 60 * 24),
            )

            const rangeIndex = ranges.findIndex(
              (range) =>
                (range.min === undefined || daysDiff >= range.min) &&
                (range.max === undefined || daysDiff < range.max),
            )

            if (rangeIndex !== -1) {
              distribution[rangeIndex].value++
            }
          }
        })

        const option = {
          title: {
            text: '小说更新频率分析',
            left: 'center',
          },
          tooltip: {
            trigger: 'item',
            formatter: '{b}: {c} 本 ({d}%)',
          },
          series: [
            {
              type: 'pie',
              radius: '55%',
              center: ['50%', '60%'],
              data: distribution,
              emphasis: {
                itemStyle: {
                  shadowBlur: 10,
                  shadowOffsetX: 0,
                  shadowColor: 'rgba(0, 0, 0, 0.5)',
                },
              },
            },
          ],
          legend: {
            orient: 'vertical',
            left: 'left',
            top: 'center',
          },
        }

        updateFrequencyChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取更新频率数据失败:', err)
      })
      .finally(() => {
        updateFrequencyChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化更新频率图失败:', err)
    ElMessage.error('初始化更新频率图失败')
    updateFrequencyChartLoading.value = false
  }
}

// 新增: 初始化标签关联分析图
const initTagRelationChart = () => {
  if (!tagRelationChartRef.value) return

  tagRelationChartLoading.value = true

  try {
    if (tagRelationChart) {
      tagRelationChart.dispose()
    }

    tagRelationChart = echarts.init(tagRelationChartRef.value)

    // 使用Novels.list获取小说列表
    api.Novels.list(1, 200)
      .then((response) => {
        if ('error' in response) {
          ElMessage.error(response.error)
          return
        }

        // 分析标签关联
        const tagRelations: Record<string, number> = {}
        const tagCounts: Record<string, number> = {}

        // 统计每个标签的出现次数和标签间的关联次数
        response.items.forEach((novel) => {
          if (novel.tags) {
            const tags = novel.tags
              .split('|')
              .map((tag) => tag.trim())
              .filter((tag) => tag)

            // 统计每个标签出现次数
            tags.forEach((tag) => {
              tagCounts[tag] = (tagCounts[tag] || 0) + 1
            })

            // 统计标签之间的关联关系
            for (let i = 0; i < tags.length; i++) {
              for (let j = i + 1; j < tags.length; j++) {
                const pair = [tags[i], tags[j]].sort().join('-')
                tagRelations[pair] = (tagRelations[pair] || 0) + 1
              }
            }
          }
        })

        // 找出最常见的标签（出现次数前10个）
        const topTags = Object.entries(tagCounts)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 10)
          .map(([tag]) => tag)

        // 准备图表数据
        const nodes = topTags.map((tag) => ({
          name: tag,
          value: tagCounts[tag],
          symbolSize: 10 + Math.sqrt(tagCounts[tag]) * 2,
        }))

        const links: { source: string; target: string; value: number }[] = []
        Object.entries(tagRelations).forEach(([pair, count]) => {
          const [source, target] = pair.split('-')
          if (topTags.includes(source) && topTags.includes(target)) {
            links.push({
              source,
              target,
              value: count,
            })
          }
        })

        const option = {
          title: {
            text: '标签关联分析',
            left: 'center',
          },
          tooltip: {
            formatter: (params) => {
              if (params.dataType === 'node') {
                return `${params.data.name}: ${params.data.value} 本小说`
              } else {
                return `${params.data.source} - ${params.data.target}: 共现 ${params.data.value} 次`
              }
            },
          },
          series: [
            {
              type: 'graph',
              layout: 'force',
              data: nodes,
              links: links,
              categories: topTags.map((tag) => ({ name: tag })),
              roam: true,
              label: {
                show: true,
                position: 'right',
              },
              force: {
                repulsion: 100,
                edgeLength: 80,
              },
              emphasis: {
                focus: 'adjacency',
              },
              lineStyle: {
                width: 2,
                curveness: 0.2,
              },
            },
          ],
        }

        tagRelationChart?.setOption(option)
      })
      .catch((err) => {
        console.error('获取标签关联数据失败:', err)
      })
      .finally(() => {
        tagRelationChartLoading.value = false
      })
  } catch (err) {
    console.error('初始化标签关联图失败:', err)
    ElMessage.error('初始化标签关联图失败')
    tagRelationChartLoading.value = false
  }
}

// 新增: 初始化词云展示面板
const initWordCloudPanel = async () => {
  wordCloudPanelLoading.value = true

  try {
    // 获取热门小说列表
    const response = await api.Novels.list(1, 3)

    if ('error' in response) {
      ElMessage.error(response.error)
      return
    }

    // 每本小说都有一个词云
    const newWordClouds: typeof wordClouds.value = []

    // 获取每本小说的词云
    for (const novel of response.items.slice(0, 3)) {
      try {
        // 获取词云Blob URL
        const wordCloudUrl = await api.Stats.fetchWordCloudBlob(novel.id)

        if (wordCloudUrl) {
          newWordClouds.push({
            novelId: novel.id,
            novelTitle: novel.title,
            wordCloudUrl: wordCloudUrl,
          })
        }
      } catch (e) {
        console.error(`获取小说${novel.id}词云失败:`, e)
      }
    }

    // 更新词云数据
    wordClouds.value = newWordClouds
  } catch (err) {
    console.error('初始化词云面板失败:', err)
    ElMessage.error('初始化词云面板失败')
  } finally {
    wordCloudPanelLoading.value = false
  }
}

// 更新图表
const updateCharts = () => {
  // 检查图表是否已初始化
  if (!trendChart) {
    initCharts()
    return
  }

  // 更新趋势图
  trendChartLoading.value = true
  try {
    const filteredData = filterDataByTimeRange(timeRange.value)

    trendChart.setOption({
      xAxis: {
        data: filteredData.map((item) => item.date),
      },
      series: [
        {
          data: filteredData.map((item) => item.count),
        },
      ],
    })
  } catch (err) {
    console.error('更新趋势图失败:', err)
  } finally {
    trendChartLoading.value = false
  }

  // 更新其他图表
  if (statusChart) initStatusChart()
  if (tagsChart) initTagsChart()
  if (chaptersChart) initChaptersChart()
  if (authorsChart) initAuthorsChart()
  if (updateFrequencyChart) initUpdateFrequencyChart()
  if (tagRelationChart) initTagRelationChart()
}

// 优化的根据时间范围过滤数据
const filterDataByTimeRange = (range: string) => {
  const uploadStats =
    uploadStatsCache.value.length > 0 ? uploadStatsCache.value : statsStore.uploadStats

  if (uploadStats.length === 0) return []

  // 获取当前日期
  const now = new Date()

  let startDate: string

  if (range === 'week') {
    // 过去 7 天
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
    startDate = weekAgo.toISOString().split('T')[0]
  } else if (range === 'month') {
    // 过去 30 天
    const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
    startDate = monthAgo.toISOString().split('T')[0]
  } else {
    // 过去 365 天
    const yearAgo = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000)
    startDate = yearAgo.toISOString().split('T')[0]
  }

  return uploadStats.filter((item) => item.date >= startDate)
}

// 优化的更新时间范围
const updateTimeRange = () => {
  if (trendChart) {
    trendChartLoading.value = true
    try {
      const filteredData = filterDataByTimeRange(timeRange.value)

      trendChart.setOption({
        xAxis: {
          data: filteredData.map((item) => item.date),
          axisLabel: {
            rotate: timeRange.value === 'year' ? 45 : 0,
          },
        },
        series: [
          {
            data: filteredData.map((item) => item.count),
          },
        ],
      })
    } catch (err) {
      console.error('更新时间范围失败:', err)
    } finally {
      trendChartLoading.value = false
    }
  }
}

// 获取封面URL
const getCoverUrl = (id: string): string => {
  return api.Novels.getCoverUrl(id)
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
    return date.toLocaleDateString('zh-CN')
  } catch (e) {
    return dateStr
  }
}

// 处理小说点击
const handleNovelClick = (row: NovelSummary) => {
  router.push(`/novel/${String(row.id)}`)
}

// 优化窗口调整大小处理，使用防抖
const handleResize = () => {
  // 清除之前的定时器
  if (resizeDebounceTimer) {
    clearTimeout(resizeDebounceTimer)
  }

  // 设置新的定时器
  resizeDebounceTimer = window.setTimeout(() => {
    // 调整所有图表大小
    if (trendChart) trendChart.resize()
    if (statusChart) statusChart.resize()
    if (tagsChart) tagsChart.resize()
    if (chaptersChart) chaptersChart.resize()
    if (authorsChart) authorsChart.resize()
    if (updateFrequencyChart) updateFrequencyChart.resize()
    if (tagRelationChart) tagRelationChart.resize()
  }, 300) // 使用更长的延迟时间
}

onMounted(() => {
  // 加载数据
  fetchData()

  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 移除窗口大小变化监听
  window.removeEventListener('resize', handleResize)

  // 销毁所有图表实例
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (statusChart) {
    statusChart.dispose()
    statusChart = null
  }
  if (tagsChart) {
    tagsChart.dispose()
    tagsChart = null
  }
  if (chaptersChart) {
    chaptersChart.dispose()
    chaptersChart = null
  }
  if (authorsChart) {
    authorsChart.dispose()
    authorsChart = null
  }
  if (updateFrequencyChart) {
    updateFrequencyChart.dispose()
    updateFrequencyChart = null
  }
  if (tagRelationChart) {
    tagRelationChart.dispose()
    tagRelationChart = null
  }

  // 清除计时器
  if (resizeDebounceTimer) {
    clearTimeout(resizeDebounceTimer)
    resizeDebounceTimer = null
  }
})
</script>

<style scoped>
.dashboard-container {
  padding-bottom: 40px;
}

.page-title {
  margin-bottom: 20px;
}

.data-card {
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

.chart-row {
  margin-top: 20px;
}

.chart-container {
  position: relative;
  height: 400px;
  padding: 10px;
}

.echarts-container {
  height: 100%;
  width: 100%;
}

.stat-card {
  text-align: center;
  padding: 20px;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-actions {
  display: flex;
  align-items: center;
}

.novel-cover-small {
  width: 40px;
  height: 60px;
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

/* 新增词云面板样式 */
.wordcloud-panel {
  padding: 10px;
  min-height: 300px;
}

.wordcloud-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: center;
}

.wordcloud-item {
  text-align: center;
  width: 300px;
}

.wordcloud-item h4 {
  margin-bottom: 10px;
}

.wordcloud-image {
  max-width: 100%;
  height: 250px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
</style>
