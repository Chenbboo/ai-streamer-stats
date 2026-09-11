import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canReportProgress, taskCompletion, readProgressSnapshot, progressEventTarget } from './projectProgress.js'
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
