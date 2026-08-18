import request from '@/utils/request'

export const chatWithBossAi = data => request({ url: '/business/ai/boss/chat', method: 'post', data })
export const getBossAiConversation = conversationId => request({ url: `/business/ai/boss/conversation/${conversationId}`, method: 'get' })
export const confirmBossAiAction = actionRequestId => request({ url: `/business/ai/boss/action/${actionRequestId}/confirm`, method: 'put' })
export const rejectBossAiAction = actionRequestId => request({ url: `/business/ai/boss/action/${actionRequestId}/reject`, method: 'put' })
