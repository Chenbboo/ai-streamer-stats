<template><div /></template>
<script setup name="BusinessResources">
import { useRoute, useRouter } from 'vue-router'
const route = useRoute(), router = useRouter()
router.replace({ path: '/business/projects', query: route.query.projectId ? { id: route.query.projectId, tab: 'resources' } : {} })
</script>
