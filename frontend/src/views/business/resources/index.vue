<template><div class="business-product"><header class="product-head"><div><h1>{{ locale==='vi-VN'?'Nhân sự và công việc thực tế':'人员安排与实际投入' }}</h1><p>{{ locale==='vi-VN'?'Chọn dự án để phân bổ nhân sự và xác nhận công việc.':'选择项目，安排人员并记录、确认实际投入。' }}</p></div></header><div class="product-toolbar"><el-select v-model="projectId" filterable @change="switchProject"><el-option v-for="p in projects" :key="p.projectId" :label="p.projectName" :value="p.projectId"/></el-select></div><BusinessProjectWorkPanel v-if="projectId" :project-id="projectId"/><el-empty v-else/></div></template>
<script setup name="BusinessResources">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getProjectWorkOptions } from '@/api/business/projectWork'
import BusinessProjectWorkPanel from '@/components/BusinessProjectWorkPanel/index.vue'
import '@/assets/styles/business-product.scss'
const route=useRoute(),router=useRouter(),{locale}=useI18n(),projects=ref([]),projectId=ref(route.query.projectId?Number(route.query.projectId):null)
function switchProject(){router.replace({query:{projectId:projectId.value}})}
onMounted(async()=>{projects.value=(await getProjectWorkOptions()).data||[];if(!projects.value.some(p=>Number(p.projectId)===Number(projectId.value)))projectId.value=projects.value[0]?.projectId||null})
</script>
