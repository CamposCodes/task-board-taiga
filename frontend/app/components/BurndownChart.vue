<script setup lang="ts">
const props = defineProps<{
  steps: { name: string; label?: string; closed: number }[]
  total: number
  known?: number
  axis: string
}>()

const H = 260
const LEFT = 56
const RIGHT = 24
const TOP = 16
const BOTTOM = 48
const FLOOR = H - BOTTOM

const box = ref<HTMLElement>()
const width = ref(900)
let observer: ResizeObserver | undefined
onMounted(() => {
  observer = new ResizeObserver(([entry]) => (width.value = Math.max(Math.round(entry!.contentRect.width), 320)))
  observer.observe(box.value!)
})
onBeforeUnmount(() => observer?.disconnect())

function roundStep(max: number) {
  const raw = Math.max(max, 1) / 5
  const power = 10 ** Math.floor(Math.log10(raw))
  return [1, 2, 5, 10].map((times) => times * power).find((step) => step >= raw)!
}

const chart = computed(() => {
  const count = Math.max(props.steps.length, 1)
  const step = roundStep(props.total)
  const top = Math.max(Math.ceil(props.total / step), 1) * step
  const x = (i: number) => LEFT + (i * (width.value - LEFT - RIGHT)) / count
  const y = (value: number) => FLOOR - (Math.max(value, 0) * (FLOOR - TOP)) / top
  let left = props.total
  const remaining = [props.total, ...props.steps.slice(0, props.known ?? props.steps.length).map((s) => (left -= s.closed))]
  const every = Math.max(Math.ceil(((count + 1) * 88) / (width.value - LEFT - RIGHT)), 1)
  const names = ['Início', ...props.steps.map((s) => s.label ?? s.name)]
  return {
    points: remaining.map((value, i) => ({ x: x(i), y: y(value), value, name: i === 0 ? 'Início' : props.steps[i - 1]!.name })),
    marks: Array.from({ length: Math.round(top / step) + 1 }, (_, i) => ({ value: i * step, y: y(i * step) })),
    ticks: names
      .map((name, i) => ({ name: name.length > 14 ? `${name.slice(0, 13)}…` : name, x: x(i), index: i }))
      .filter((tick) => tick.index % every === 0),
  }
})
</script>

<template>
  <div ref="box" class="chart">
    <svg :width="width" :height="H" :viewBox="`0 0 ${width} ${H}`" role="img" :aria-label="`Pontos em aberto, por ${axis.toLowerCase()}`">
      <g font-size="11" fill="#555">
        <template v-for="mark in chart.marks" :key="mark.value">
          <line :x1="LEFT" :y1="mark.y" :x2="width - RIGHT" :y2="mark.y" stroke="#e0e0e0" />
          <text :x="LEFT - 8" :y="mark.y + 4" text-anchor="end">{{ number(mark.value) }}</text>
        </template>
        <template v-for="tick in chart.ticks" :key="tick.index">
          <line :x1="tick.x" :y1="TOP" :x2="tick.x" :y2="FLOOR" stroke="#e0e0e0" />
          <text :x="tick.x" :y="FLOOR + 18" text-anchor="middle">{{ tick.name }}</text>
        </template>
        <text x="14" :y="(TOP + FLOOR) / 2" text-anchor="middle" :transform="`rotate(-90 14 ${(TOP + FLOOR) / 2})`">Pontos</text>
        <text :x="(LEFT + width - RIGHT) / 2" :y="H - 8" text-anchor="middle">{{ axis }}</text>
      </g>
      <polyline :points="chart.points.map((p) => `${p.x},${p.y}`).join(' ')" fill="none" stroke="#10948b" stroke-width="2" />
      <circle v-for="point in chart.points" :key="point.x" :cx="point.x" :cy="point.y" r="4" fill="#006557">
        <title>{{ point.name }}: {{ number(point.value) }} pontos em aberto</title>
      </circle>
    </svg>
  </div>
</template>
