import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { canReportProgress, taskCompletion, readProgressSnapshot, progressEventTarget, monthlyProgressPercent, projectProgressLimit, projectProgressBarPercent, progressSubmissionIssue, isExcessCompletion, completionStandardForProgress } from './projectProgress.js'
test('only the active or paused child owner has the submit action',()=>{
 const child={parentId:1,mainOwnerUserId:9,status:'ACTIVE'}
 assert.equal(canReportProgress(child,'9'),true)
 assert.equal(canReportProgress(child,1),false)
 assert.equal(canReportProgress({...child,status:'CLOSED'},9),false)
 assert.equal(canReportProgress({...child,parentId:null},9),false)
 assert.equal(canReportProgress({...child,status:'PAUSED'},9),true)
})
test('task completion excludes canceled tasks and handles empty projects',()=>{
 assert.deepEqual(taskCompletion([]),{total:0,done:0,percent:0})
 assert.deepEqual(taskCompletion([{status:'DONE'},{status:'DOING'},{status:'CANCELED'}]),{total:2,done:1,percent:50})
})
test('history uses its saved snapshot and malformed legacy data stays explicit',()=>{
 assert.deepEqual(readProgressSnapshot({snapshotJson:'{"tasks":[{"progress":30}]}'}),{tasks:[{progress:30}]})
 assert.equal(readProgressSnapshot({}),null)
 assert.equal(readProgressSnapshot({snapshotJson:'broken'}),null)
})
test('only server progress event format provides a child and report link',()=>{
 assert.deepEqual(progressEventTarget({eventType:'SUBPROJECT_PROGRESS',comment:'[子项目:20][汇报:31] 子项目'}),{projectId:20,reportId:31})
 assert.equal(progressEventTarget({eventType:'EDIT',comment:'[子项目:20][汇报:31]'}),null)
 assert.deepEqual(progressEventTarget({eventType:'PROJECT_PROGRESS',comment:'[子项目:20][汇报:31] 汇报 v2'}),{projectId:20,reportId:31})
})

test('an unreported new month is not a reported zero percent or task completion',()=>{
 assert.equal(monthlyProgressPercent({status:'ACTIVE',progressPercent:0,taskCount:1,completedTaskCount:1}),null)
 assert.equal(monthlyProgressPercent({status:'ACTIVE',progressReportId:17,progressPercent:0}),0)
 assert.equal(monthlyProgressPercent({status:'PAUSED',progressReportId:17,progressPercent:'38'}),38)
 assert.equal(monthlyProgressPercent({status:'ACTIVE',progressReportId:17,progressPercent:null}),null)
})
test('terminal project progress remains unchanged without a monthly report',()=>{
 assert.equal(monthlyProgressPercent({status:'CLOSED',progressPercent:0}),100)
 assert.equal(monthlyProgressPercent({status:'CANCELED',progressPercent:80}),0)
 assert.equal(monthlyProgressPercent({status:'ACTIVE',progressReportId:17,progressPercent:120}),120)
})
test('detail separates whole-project time from monthly reported completion',()=>{
 const source=readFileSync(new URL('../views/business/project/index.vue',import.meta.url),'utf8')
 assert.doesNotMatch(source,/scheduleProgress\.value\s*>\s*projectProgress|scheduleProgress>projectProgress|进度落后时间计划/)
 assert.match(source,/时间进度按全项目周期计算，不与本月完成率比较/)
 assert.match(source,/本月尚未汇报/)
})

test('every monthly report uses the same 300% limit and scale, including legacy reports',()=>{
 const standard={status:'ACTIVE',progressReportId:17,progressPercent:180,progressCompletionStandard:'STANDARD'}
 const excess={...standard,progressCompletionStandard:'EXCESS'}
 assert.equal(monthlyProgressPercent(standard),180)
 assert.equal(monthlyProgressPercent(excess),180)
 assert.equal(monthlyProgressPercent({...excess,progressPercent:300}),300)
 assert.equal(monthlyProgressPercent({...excess,progressPercent:400}),300)
 assert.equal(projectProgressLimit(excess),300)
 assert.equal(projectProgressLimit({}),300)
 assert.equal(projectProgressBarPercent(excess,150),50)
 assert.equal(projectProgressBarPercent(excess,300),100)
 assert.equal(projectProgressBarPercent(standard,100),100/3)
 assert.equal(isExcessCompletion(standard),true)
 assert.equal(isExcessCompletion({...excess,progressPercent:80}),false)
 assert.equal(projectProgressBarPercent(excess,NaN),0)
 assert.equal(projectProgressBarPercent(excess,-1),0)
 assert.equal(monthlyProgressPercent({...excess,progressReportId:null}),null)
})

test('monthly progress accepts 0–300 integers without selecting a standard',()=>{
 for(const progress of [0,100,101,220,300])assert.equal(progressSubmissionIssue({progress}),null)
 for(const progress of [null,undefined,-1,301,1.5,'100',NaN,Infinity])assert.equal(progressSubmissionIssue({progress}),'range')
 assert.equal(progressSubmissionIssue({progress:150},200),'minimum')
 assert.equal(progressSubmissionIssue({progress:200},200),null)
 assert.equal(completionStandardForProgress(100),'STANDARD')
 assert.equal(completionStandardForProgress(101),'EXCESS')
 assert.equal(completionStandardForProgress(300),'EXCESS')
})
