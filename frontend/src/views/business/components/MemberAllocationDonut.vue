<template>
  <div class="allocation-donut" @mouseleave="hovered = null">
    <div ref="chart" class="allocation-donut__chart" role="img" :aria-label="description" />
    <button class="allocation-donut__center" type="button" :aria-label="$tr('查看 {0} 的投入详情', [member.userName])" @click="$emit('browse')">
      <strong :class="{'allocation-donut__warning': member.status === 'over'}">{{ centerValue }}</strong>
      <span>{{ centerLabel }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { memberAllocationSlices } from '@/utils/memberProjectAllocations'
import { translateText } from '@/locales/translate'

echarts.use([PieChart, TooltipComponent, SVGRenderer])
const props = defineProps({ member: { type: Object, required: true }, activeProjectId: [Number, String] })
const emit = defineEmits(['select', 'browse'])
const chart = ref(), hovered = ref(null)
const slices = computed(() => memberAllocationSlices(props.member))
const percent = value => Number(value).toFixed(2) + '%'
const focused = computed(() => hovered.value || (props.activeProjectId != null ? slices.value.find(slice => slice.kind === 'project' && String(slice.projectId) === String(props.activeProjectId)) : null))
const centerValue = computed(() => focused.value?.kind === 'project' ? percent(focused.value.value) : props.member.failed || !props.member.projects.some(project => project.percent !== null) ? '—' : percent(props.member.total))
const centerLabel = computed(() => focused.value?.kind === 'project' ? focused.value.name : translateText(props.member.incomplete ? '已知投入合计' : '投入合计'))
const description = computed(() => props.member.userName + ': ' + props.member.projects.map(project => project.projectName + ' ' + (project.percent == null ? translateText('未配置 / 待确认') : percent(project.percent))).join('; '))
let instance, observer, disposed = false

async function render() {
  await nextTick()
  if (disposed || !chart.value) return
  instance ||= echarts.init(chart.value, null, { renderer: 'svg' })
  instance.setOption({
    animationDuration: 400,
    tooltip: { trigger: 'item', renderMode: 'richText', confine: true, formatter: event => {
      const slice = slices.value[event.dataIndex]
      if (slice.kind === 'unknown') return translateText('投入信息待完善')
      if (slice.kind === 'unallocated') return translateText('尚未分配') + '\n' + percent(slice.value)
      return slice.name + '\n' + translateText('项目投入比例') + ': ' + percent(slice.value)
    } },
    series: [{ type: 'pie', radius: ['69%', '88%'], center: ['50%', '50%'], startAngle: 90,
      selectedMode: 'single', selectedOffset: 5, label: { show: false }, labelLine: { show: false },
      emphasis: { scaleSize: 4, itemStyle: { shadowBlur: 10, shadowColor: 'rgba(38, 72, 117, .12)' } },
      data: slices.value.map(slice => ({ name: slice.name || translateText(slice.kind === 'unknown' ? '投入信息待完善' : '尚未分配'), value: slice.value,
        selected: slice.kind === 'project' && String(slice.projectId) === String(props.activeProjectId),
        itemStyle: { color: slice.color, borderRadius: 5, borderColor: 'white', borderWidth: 3,
          opacity: props.activeProjectId != null && slice.kind === 'project' && String(slice.projectId) !== String(props.activeProjectId) ? .25 : 1 }
      }))
    }]
  }, true)
  instance.off('click'); instance.off('mouseover'); instance.off('mouseout')
  instance.on('click', event => { const slice = slices.value[event.dataIndex]; if (slice?.kind === 'project') emit('select', slice.projectId); else emit('browse') })
  instance.on('mouseover', event => { hovered.value = slices.value[event.dataIndex] || null })
  instance.on('mouseout', () => { hovered.value = null })
  observer ||= new ResizeObserver(() => instance?.resize())
  observer.disconnect(); observer.observe(chart.value)
}
watch(() => [props.member, props.activeProjectId], () => { hovered.value = null; render() })
onMounted(render)
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); instance?.dispose() })
</script>

<style scoped>
.allocation-donut{position:relative;width:156px;height:156px;flex-shrink:0}.allocation-donut__chart{width:100%;height:100%}.allocation-donut__center{position:absolute;left:21%;top:28%;width:58%;height:44%;padding:0;border:0;background:transparent;cursor:pointer;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;color:var(--el-text-color-primary);border-radius:50%}.allocation-donut__center:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px}.allocation-donut__center strong{font-size:22px;letter-spacing:-.7px;font-weight:650;font-variant-numeric:tabular-nums}.allocation-donut__center span{max-width:100%;font-size:11px;color:var(--el-text-color-secondary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.allocation-donut__warning{color:var(--el-color-danger)}
@container(max-width:1100px){.allocation-donut{width:148px;height:148px}}
</style>
