<template>
  <article class="acceptance-card">
    <header>
      <div>
        <el-tag size="small" type="warning">{{ $tr("待老板验收") }}</el-tag>
        <h3>{{ review.project?.projectName }}</h3>
        <p>{{ $tr("{0} · 负责人 {1}", [review.project?.companyName, review.project?.mainOwnerName]) }}</p>
      </div>
      <div class="version">{{ $tr("第 {0} 版", [review.acceptance?.submissionVersion]) }}<br><small>{{ review.acceptance?.submittedTime }}</small></div>
    </header>

    <div class="summary-grid">
      <div><small>{{ $tr("一次性任务") }}</small><b>{{ $tr("{0}/{1} 已完成", [review.completedTaskCount, review.taskCount]) }}</b></div>
      <div><small>{{ $tr("里程碑") }}</small><b>{{ $tr("{0}/{1} 已完成", [review.completedMilestoneCount, review.milestoneCount]) }}</b></div>
      <div><small>{{ $tr("未关闭高风险") }}</small><b :class="{ danger: review.openHighRiskCount > 0 }">{{ $tr("{0} 项", [review.openHighRiskCount]) }}</b></div>
      <div><small>{{ $tr("交付凭证") }}</small><b>{{ $tr("{0} 份", [review.attachmentCount]) }}</b></div>
    </div>

    <section><small>{{ $tr("成果说明") }}</small><p>{{ review.acceptance?.resultSummary || $tr("负责人未填写成果说明") }}</p></section>
    <section><small>{{ $tr("交付内容") }}</small><p>{{ review.acceptance?.deliverables || $tr("负责人未填写交付内容") }}</p></section>

    <section v-if="attachments.length" class="attachments">
      <small>{{ $tr("成果凭证") }}</small>
      <div><el-button v-for="(item,index) in attachments" :key="item" plain type="primary" @click="showAttachment(item)">{{ $tr("查看凭证 {0}", [index + 1]) }}</el-button></div>
    </section>
    <section v-if="review.warnings?.length" class="warnings">
      <small>{{ $tr("需要老板注意") }}</small><p v-for="item in review.warnings" :key="item">{{ item }}</p>
    </section>
    <div class="recommendation" :class="review.canApprove?'can-approve':'cannot-approve'">{{ review.recommendation }}</div>

    <div v-if="!compact" class="decision-area">
      <el-input v-model="returnReason" maxlength="300" :placeholder="$tr(&quot;如需退回，请写明负责人要补充或修改什么&quot;)" />
      <div>
        <el-button type="warning" plain :disabled="!returnReason.trim()" @click="returnAcceptance">{{ $tr("退回负责人补充") }}</el-button>
        <el-button type="primary" :disabled="!review.canApprove" @click="approveAcceptance">{{ $tr("验收通过并结项") }}</el-button>
      </div>
    </div>

    <el-dialog v-model="previewVisible" :title="$tr(&quot;成果凭证&quot;)" width="min(860px,92vw)" append-to-body>
      <business-file-upload :model-value="previewUrl" :project-id="review.project?.projectId" disabled :drag="false" :is-show-tip="false" />
    </el-dialog>
  </article>
</template>

<script setup>
import { translateText } from '@/locales/translate'

const props=defineProps({review:{type:Object,required:true},compact:{type:Boolean,default:false}})
const emit=defineEmits(['action'])
const returnReason=ref('')
const previewVisible=ref(false)
const previewUrl=ref('')
const attachments=computed(()=>Array.isArray(props.review.attachmentList)?props.review.attachmentList:[])
function showAttachment(value){previewUrl.value=value;previewVisible.value=true}
function approveAcceptance(){emit('action',translateText("验收通过并结项“{0}”", [props.review.project?.projectName]))}
function returnAcceptance(){emit('action',translateText("退回验收“{0}”，原因：{1}", [props.review.project?.projectName, returnReason.value.trim()]))}
</script>

<style scoped>
.acceptance-card{margin-top:12px;padding:18px;border:1px solid #b9ddd4;border-radius:13px;background:#fbfffe;box-shadow:0 7px 18px rgba(37,94,85,.08)}
header{display:flex;justify-content:space-between;gap:18px;border-bottom:1px solid #dfebe8;padding-bottom:14px}h3{margin:8px 0 4px;font-size:19px;color:#183b43}header p{margin:0;color:#71868b;font-size:12px}.version{text-align:right;color:#28766d;font-weight:700}.version small{color:#8a9a9d;font-weight:400}
.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px;margin:14px 0}.summary-grid>div{padding:11px;border-radius:8px;background:#edf7f5}.summary-grid small,.acceptance-card section>small{display:block;color:#71878a}.summary-grid b{display:block;margin-top:5px;color:#24434a}.summary-grid .danger{color:#d94b4b}
section{margin-top:10px;padding:11px;border-radius:8px;background:#f5f8f8}section p{margin:6px 0 0;line-height:1.65;white-space:pre-line}.attachments>div{display:flex;flex-wrap:wrap;gap:8px;margin-top:9px}.warnings{background:#fff4e6;color:#805821}.warnings p:before{content:'• '}.recommendation{margin-top:11px;padding:11px;border-radius:8px;font-weight:650}.can-approve{background:#eaf7ef;color:#24714d}.cannot-approve{background:#fff0ed;color:#a44a3f}
.decision-area{display:flex;align-items:center;gap:10px;margin-top:14px}.decision-area>.el-input{flex:1}.decision-area>div{display:flex;flex:0 0 auto;gap:8px}.preview-image{display:block;max-width:100%;max-height:70vh;margin:auto}.file-preview{text-align:center;padding:30px}
@media(max-width:700px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.decision-area{align-items:stretch;flex-direction:column}.decision-area>div{display:grid;grid-template-columns:1fr 1fr}.decision-area .el-button{margin:0}.version{font-size:12px}}
</style>
