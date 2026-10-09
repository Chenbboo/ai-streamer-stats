import test from 'node:test'
import assert from 'node:assert/strict'
import { filterMemberCompletionReports, mergeMemberCompletionReports } from './memberCompletionReports.js'

const records = [
  { reportId:1,memberUserId:7,memberName:'成员甲',projectId:10,workType:'ROUTINE',reportDate:'2026-10-01',reportDetails:'完成素材' },
  { reportId:1,memberUserId:7,memberName:'成员甲',projectId:20,workType:'TASK',reportDate:'2026-10-02',reportDetails:'提交初稿' },
  { reportId:2,memberUserId:8,memberName:'成员甲',projectId:10,workType:'TASK',reportDate:'2026-10-03',reportDetails:'完成审核' }
]
test('combines member, project and inclusive date filters without merging different report types',()=>{
  assert.equal(filterMemberCompletionReports(records).length,3)
  assert.deepEqual(filterMemberCompletionReports(records,{memberUserId:'7',projectId:'20',dates:['2026-10-02','2026-10-02']}).map(row=>row.reportDetails),['提交初稿'])
  assert.equal(filterMemberCompletionReports(records,{memberUserId:'7',projectId:'10',dates:['2026-10-02','2026-10-03']}).length,0)
  assert.deepEqual(filterMemberCompletionReports(records,{memberUserId:'7'}).map(row=>row.workType),['TASK','ROUTINE'])
})
test('cleared filters restore history and sort across projects without changing the source',()=>{
  assert.deepEqual(filterMemberCompletionReports(records,{memberUserId:'',projectId:'',dates:null}).map(row=>row.reportDate),['2026-10-03','2026-10-02','2026-10-01'])
  assert.equal(records[0].reportDate,'2026-10-01')
})
test('preserves every version of the same daily report and lists the later submission first',()=>{
  const versions=[
    {...records[0],submissionId:1,submittedTime:'2026-10-01 12:00:00',evidenceUrls:'/profile/old.pdf'},
    {...records[0],submissionId:2,submittedTime:'2026-10-01 12:00:00',reportDetails:'补充素材',evidenceUrls:'/profile/new.pdf'}
  ]
  const result=filterMemberCompletionReports(versions,{memberUserId:7,dates:['2026-10-01','2026-10-01']})
  assert.equal(result.length,2)
  assert.deepEqual(result.map(row=>row.evidenceUrls),['/profile/new.pdf','/profile/old.pdf'])
})

test('active work reports retain member, project, period, content and attachments alongside completion records',()=>{
 const workspace={project:{projectId:10,projectName:'项目甲'},memberCompletionReports:records.slice(0,1),workReports:[{reportId:1,submittedUserId:8,submittedUserName:'成员乙',frequency:'WEEKLY',periodStart:'2026-09-28',periodEnd:'2026-10-04',createTime:'2026-10-05 09:00:00',content:'本周工作内容',attachmentUrls:'["/file/a.pdf"]'}]}
 const rows=mergeMemberCompletionReports(workspace)
 assert.equal(rows.length,2)
 const [report]=filterMemberCompletionReports(rows,{memberUserId:'8'})
 assert.equal(report.workType,'WORK_REPORT')
 assert.equal(report.projectName,'项目甲')
 assert.equal(report.reportDate,'2026-10-05')
 assert.equal(report.periodStart,'2026-09-28')
 assert.equal(report.periodEnd,'2026-10-04')
 assert.equal(report.frequency,'WEEKLY')
 assert.equal(report.reportDetails,'本周工作内容')
 assert.equal(report.evidenceUrls,'["/file/a.pdf"]')
 assert.equal(workspace.memberCompletionReports.length,1)
})
