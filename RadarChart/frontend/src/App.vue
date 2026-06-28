<template>
  <div class="wrap">
    <h2>力扣能力分析 <span class="help-icon" @click.stop="showNotice = !showNotice">?</span></h2>
    <div class="box input-box">
      <label>Cookie</label>
      <textarea v-model="cookie" placeholder="从浏览器开发者工具复制完整 Cookie..." rows="1"></textarea>
      <button @click="start" :disabled="loading">{{ loading ? '请求中...' : '开始' }}</button>
    </div>
    <div v-if="err" class="err">{{ err }}</div>
    <div v-if="loading" class="box">
      <div class="bar-bg"><div class="bar" :style="{ width: pct + '%' }"></div></div>
      <div class="msg">{{ msg }}</div>
    </div>
    <div v-if="chartReady" ref="cogChart" class="chart chart-cog"></div>
    <div v-if="chartReady" ref="totalChart" class="chart"></div>
    <div v-if="chartReady" class="tier-box">
      <div v-if="masterTags.length" class="tier-section">
        <div class="tier-title red">宗师级 (80-100)</div>
        <div class="tier-tags"><span v-for="t in masterTags" :key="t.name" class="tier-badge red">{{ t.name }} {{ t.mastery }}%</span></div>
      </div>
      <div v-if="expertTags.length" class="tier-section">
        <div class="tier-title orange">专家级 (50-80)</div>
        <div class="tier-tags"><span v-for="t in expertTags" :key="t.name" class="tier-badge orange">{{ t.name }} {{ t.mastery }}%</span></div>
      </div>
      <div v-if="competentTags.length" class="tier-section">
        <div class="tier-title blue">熟练级 (20-50)</div>
        <div class="tier-tags"><span v-for="t in competentTags" :key="t.name" class="tier-badge blue">{{ t.name }} {{ t.mastery }}%</span></div>
      </div>
      <div v-if="noviceTags.length" class="tier-section">
        <div class="tier-title gray">入门级 (0-20)</div>
        <div class="tier-tags"><span v-for="t in noviceTags" :key="t.name" class="tier-badge gray">{{ t.name }} {{ t.mastery }}%</span></div>
      </div>
    </div>
    <div v-if="chartReady" class="box detail-list">
      <h3>全部模块统计 (共 {{ total }} 题 · {{ tagList.length }} 个标签)</h3>
      <div class="legend-row">
        <span class="dot e"></span>简单
        <span class="dot m"></span>中等
        <span class="dot h"></span>困难
        <span style="margin-left:8px;">🏆掌握度</span>
      </div>
      <table>
        <thead><tr><th>#</th><th>标签</th><th>总数</th><th>简单</th><th>中等</th><th>困难</th><th>掌握度</th></tr></thead>
        <tbody>
          <tr v-for="(t, i) in tagList" :key="t.name">
            <td>{{ i + 1 }}</td>
            <td>{{ t.name }}</td>
            <td>{{ t.total }}</td>
            <td>{{ t.easy }}</td>
            <td>{{ t.medium }}</td>
            <td>{{ t.hard }}</td>
            <td>{{ t.mastery }}%</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      class="avatar-wrap"
      :style="{ right: avatarX + 'px', bottom: avatarY + 'px' }"
      @mousedown.prevent="onAvatarDown"
    >
      <img src="/imgs/1.jpg" class="avatar-img" />
    </div>
    <div v-if="showInfo" class="info-popup" :style="{ right: (avatarX - 10) + 'px', bottom: (avatarY + 58) + 'px' }">
      <div class="info-title">制作者：宫商角徵</div>
      <div class="info-line">上次更新：{{ updatetime }}</div>
    </div>
    <div v-if="showNotice" class="notice-overlay" @click.self="showNotice = false">
      <div class="notice-box">
        <div class="notice-header">
          <span>使用须知</span>
          <span class="notice-close" @click="showNotice = false">&times;</span>
        </div>
        <div class="notice-body" v-html="noticeHtml"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onMounted } from 'vue'
import * as echarts from 'echarts'

const COG_NAMES = ['数据结构 (DS)', '算法 (AL)', '模板 (TM)', '思维 (TH)', '工程 (EG)', '直觉 (IN)']

const cookie = ref('')
const loading = ref(false)
const err = ref('')
const msg = ref('')
const pct = ref(0)
const chartData = ref(null)
const chartReady = ref(false)
const cogChart = ref(null)
const totalChart = ref(null)
const total = ref(0)
const tagList = ref([])
const updatetime = ref('加载中...')
const showInfo = ref(false)
const showNotice = ref(false)
const noticeHtml = ref('')
const noticeCached = ref('')
const avatarX = ref(16)
const avatarY = ref(16)
let cogInst = null
let totalInst = null
let dragging = false
let dragStartX = 0
let dragStartY = 0
let dragOrigX = 0
let dragOrigY = 0

const masterTags = computed(() => tagList.value.filter(t => t.mastery >= 80))
const expertTags = computed(() => tagList.value.filter(t => t.mastery >= 50 && t.mastery < 80))
const competentTags = computed(() => tagList.value.filter(t => t.mastery >= 20 && t.mastery < 50))
const noviceTags = computed(() => tagList.value.filter(t => t.mastery > 0 && t.mastery < 20))

onMounted(async () => {
  try {
    const r = await fetch('/api/info')
    if (r.ok) {
      const d = await r.json()
      updatetime.value = d.updatetime || '未知'
    }
  } catch (_) { updatetime.value = '读取失败' }
})

async function fetchNotice() {
  try {
    const r = await fetch('/api/notice')
    if (r.ok) {
      const d = await r.json()
      const html = md2html(d.content || '')
      noticeHtml.value = html
      noticeCached.value = html
      return
    }
  } catch (_) {}
  noticeHtml.value = noticeCached.value || '<p>内容加载失败</p>'
}

function md2html(md) {
  let html = md
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\*(.+?)\*/g, '<i>$1</i>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" />')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
    .replace(/\n/g, '<br>')
  return html
}

watch(showNotice, v => { if (v) fetchNotice() })

function onAvatarDown(e) {
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragOrigX = avatarX.value
  dragOrigY = avatarY.value
  dragging = true
  e.preventDefault()
}

function onMouseMove(e) {
  if (!dragging) return
  avatarX.value = Math.max(0, dragOrigX - (e.clientX - dragStartX))
  avatarY.value = Math.max(0, dragOrigY - (e.clientY - dragStartY))
}

function onMouseUp(e) {
  if (!dragging) return
  const dx = Math.abs(e.clientX - dragStartX)
  const dy = Math.abs(e.clientY - dragStartY)
  dragging = false
  if (dx < 3 && dy < 3) {
    showInfo.value = !showInfo.value
  }
}

window.addEventListener('mousemove', onMouseMove)
window.addEventListener('mouseup', onMouseUp)

async function start() {
  const c = cookie.value.trim()
  if (!c) { err.value = '请输入 Cookie'; return }

  err.value = ''
  msg.value = ''
  pct.value = 0
  chartData.value = null
  chartReady.value = false
  loading.value = true

  try {
    const resp = await fetch('/api/analyze/sse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cookie: c })
    })
    if (!resp.ok) {
      const d = await resp.json()
      throw new Error(d.error || 'HTTP ' + resp.status)
    }

    const reader = resp.body.getReader()
    const dec = new TextDecoder()
    let buf = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      const lines = buf.split('\n')
      buf = lines.pop() || ''
      for (const line of lines) {
        const t = line.trim()
        if (!t || !t.startsWith('data:')) continue
        const json = t.substring(5).trim()
        if (!json) continue
        try {
          const p = JSON.parse(json)
          if (p.status === 'RUNNING') {
            pct.value = (p.currentStep / p.totalSteps) * 100
            msg.value = p.message
          } else if (p.status === 'SUCCESS') {
            chartData.value = p.data
            loading.value = false
          } else if (p.status === 'ERROR') {
            throw new Error(p.message)
          }
        } catch (e) {
          err.value = e.message
          loading.value = false
          return
        }
      }
    }
  } catch (e) {
    err.value = e.message
    loading.value = false
  }
}

function draw(data) {
  total.value = data.total || 0
  const tags = data.tags || []
  tagList.value = tags
  drawCogChart(data.cognitive || {})
  drawTotalChart(tags)
}

function drawCogChart(cognitive) {
  if (!cogChart.value) return
  const vals = []
  for (let d = 0; d < 6; d++) vals.push(cognitive[d] || 0)

  if (cogInst) cogInst.dispose()
  cogInst = echarts.init(cogChart.value)
  cogInst.setOption({
    title: { text: '认知维度画像', left: 'center', textStyle: { fontSize: 15, fontWeight: 'bold', color: '#1a5276' } },
    tooltip: { trigger: 'item' },
    radar: {
      indicator: COG_NAMES.map(n => ({ name: n, max: 100 })),
      center: ['50%', '55%'],
      radius: '65%',
      axisName: { fontSize: 13, color: '#333', fontWeight: 'bold' },
      splitArea: { areaStyle: { color: ['rgba(26,82,118,0.05)', 'rgba(26,82,118,0.1)', 'rgba(26,82,118,0.15)', 'rgba(26,82,118,0.2)'] } }
    },
    series: [{
      type: 'radar',
      data: [{ value: vals, name: '掌握度 %', areaStyle: { color: 'rgba(26,82,118,0.2)' }, lineStyle: { color: '#1a5276', width: 2 } }],
      label: { show: true, formatter: (p) => p.value + '%', fontSize: 11, color: '#1a5276' }
    }]
  })
}

function drawTotalChart(tags) {
  if (!totalChart.value) return
  const names = tags.map(t => t.name)
  const vals = tags.map(t => t.total)
  const rawMax = Math.max(...vals, 1)
  const max = rawMax < 10 ? 10 : Math.ceil((rawMax * 1.2) / 10) * 10

  if (totalInst) totalInst.dispose()
  totalChart.value.style.height = Math.max(window.innerWidth * 0.7, 500 + tags.length * 55) + 'px'
  totalInst = echarts.init(totalChart.value)
  totalInst.setOption({
    title: { text: '全标签做题数 (往下翻)', left: 'center', textStyle: { fontSize: 14, fontWeight: 'bold', color: '#888' } },
    tooltip: {
      trigger: 'item',
      formatter: function (p) {
        const tag = tags[p.dataIndex]
        return tag.name + '<br/>总数: ' + tag.total + '<br/>简单: ' + tag.easy + '  中等: ' + tag.medium + '  困难: ' + tag.hard
      }
    },
    radar: {
      indicator: names.map(n => ({ name: n, max })),
      center: ['50%', '55%'],
      radius: '75%',
      axisName: { fontSize: 14, color: '#333', fontWeight: 'bold' }
    },
    series: [{
      type: 'radar',
      data: [{ value: vals, name: '已解', areaStyle: { color: 'rgba(74,144,217,0.2)' } }]
    }]
  })
}

watch(chartData, v => { if (v) { chartReady.value = true; nextTick(() => draw(v)) } })
window.addEventListener('resize', () => { if (cogInst) cogInst.resize(); if (totalInst) totalInst.resize() })
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font: 14px/1.5 "Microsoft YaHei", sans-serif; background: #f0f0f0; color: #333; }
.wrap { width: 97vw; margin: 4px auto; padding: 0 6px; }
h2 { font-size: 16px; margin: 2px 0 3px 0; }
.box { background: #fff; border: 1px solid #ddd; padding: 4px 8px; margin-bottom: 3px; }
.input-box { padding: 8px 10px; margin-bottom: 4px; }
.box label { display: block; margin-bottom: 2px; font-weight: bold; font-size: 12px; }
textarea { width: 100%; border: 1px solid #ccc; padding: 3px 5px; font: 10px monospace; resize: vertical; }
textarea:focus { border-color: #666; outline: none; }
button { margin-top: 4px; padding: 3px 12px; border: 1px solid #888; background: #eee; cursor: pointer; font-size: 11px; }
button:hover:not(:disabled) { background: #ddd; }
button:disabled { color: #999; }
.err { background: #ffe0e0; border: 1px solid #eaa; padding: 3px 6px; margin-bottom: 3px; color: #c00; font-size: 12px; }
.bar-bg { height: 4px; background: #ddd; }
.bar { height: 100%; background: #4a90d9; }
.msg { margin-top: 2px; color: #666; font-size: 11px; }
.chart { width: 100%; background: #fff; border: 1px solid #ddd; min-height: 420px; overflow-x: hidden; margin-bottom: 3px; }
.chart-cog { min-height: 480px; height: 520px; }

.tier-box { background: #fff; border: 1px solid #ddd; padding: 6px 10px; margin-bottom: 3px; }
.tier-section { margin-bottom: 4px; }
.tier-title { font-size: 12px; font-weight: bold; margin-bottom: 2px; }
.tier-title.red { color: #c0392b; }
.tier-title.orange { color: #e67e22; }
.tier-title.blue { color: #2980b9; }
.tier-title.gray { color: #888; }
.tier-tags { display: flex; flex-wrap: wrap; gap: 3px; }
.tier-badge { display: inline-block; padding: 1px 5px; border-radius: 3px; font-size: 10px; white-space: nowrap; }
.tier-badge.red { background: #fde8e8; color: #c0392b; border: 1px solid #f5c6cb; }
.tier-badge.orange { background: #fef3e2; color: #e67e22; border: 1px solid #fde2c3; }
.tier-badge.blue { background: #e8f0fe; color: #2980b9; border: 1px solid #c4d7f2; }
.tier-badge.gray { background: #f5f5f5; color: #888; border: 1px solid #ddd; }
.detail-list h3 { font-size: 12px; margin-bottom: 3px; }
.detail-list table { width: 100%; border-collapse: collapse; font-size: 11px; }
.detail-list th, .detail-list td { padding: 2px 3px; text-align: center; border-bottom: 1px solid #eee; }
.detail-list th { background: #f5f5f5; font-weight: bold; }
.legend-row { display: flex; gap: 8px; margin-bottom: 3px; font-size: 12px; align-items: center; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 1px; vertical-align: middle; }
.dot.e { background: #4caf50; }
.dot.m { background: #ff9800; }
.dot.h { background: #f44336; }

.avatar-wrap { position: fixed; width: 50px; height: 50px; border-radius: 50%; overflow: hidden; border: 2px solid #fff; box-shadow: 0 2px 8px rgba(0,0,0,.25); cursor: pointer; z-index: 9999; user-select: none; background: #eee; }
.avatar-img { width: 100%; height: 100%; object-fit: cover; pointer-events: none; }
.info-popup { position: fixed; background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 12px 16px; box-shadow: 0 4px 16px rgba(0,0,0,.18); z-index: 9998; min-width: 200px; }
.info-title { font-size: 14px; font-weight: bold; margin-bottom: 6px; color: #333; }
.info-line { font-size: 12px; color: #666; }
.help-icon { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; border: 1.5px solid #888; color: #888; font-size: 12px; font-weight: bold; cursor: pointer; vertical-align: middle; }
.help-icon:hover { border-color: #333; color: #333; background: #f5f5f5; }
.notice-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 10000; display: flex; align-items: center; justify-content: center; }
.notice-box { background: #fff; border-radius: 10px; max-width: 700px; width: 90vw; max-height: 80vh; display: flex; flex-direction: column; box-shadow: 0 8px 32px rgba(0,0,0,.25); }
.notice-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; border-bottom: 1px solid #eee; font-size: 15px; font-weight: bold; }
.notice-close { font-size: 22px; cursor: pointer; color: #999; line-height: 1; }
.notice-close:hover { color: #333; }
.notice-body { padding: 16px; overflow-y: auto; font-size: 14px; line-height: 1.8; color: #333; }
.notice-body h1, .notice-body h2, .notice-body h3 { margin: 10px 0 6px; }
.notice-body img { max-width: 100%; border-radius: 4px; margin: 6px 0; }
.notice-body code { background: #f5f5f5; padding: 1px 5px; border-radius: 3px; font-size: 13px; }
.notice-body a { color: #2980b9; }
</style>
