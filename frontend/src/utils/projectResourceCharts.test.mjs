import test from 'node:test'
import assert from 'node:assert/strict'
import { personnelDistribution, budgetUsage } from './projectResourceCharts.js'

test('personnel slices conserve positive costs while reversals and zero members stay outside the denominator',()=>{
  const rows=[1,2,3,4,5,6,7].map(userId=>({userId,amount:userId*10}))
  rows.push({userId:8,amount:-20},{userId:9,amount:0})
  const result=personnelDistribution(rows)
  assert.equal(result.total,280)
  assert.equal(result.slices.reduce((sum,row)=>sum+row.value,0),280)
  assert.equal(result.slices.at(-1).members.length,2)
  assert.equal(result.negative[0].userId,8)
  assert.equal(rows.length,9)
})
test('budget overrun keeps its real percent while clamping the bar, and pending costs cannot claim a remaining limit',()=>{
  assert.deepEqual(budgetUsage({limit:100,used:125}),{known:true,percent:125,progress:100,exceeded:true})
  assert.equal(budgetUsage({limit:100,used:20,pendingCount:1}).known,false)
  assert.equal(budgetUsage({limit:null,used:20}).percent,null)
  assert.equal(budgetUsage({limit:0,used:20}).exceeded,true)
  assert.equal(budgetUsage({limit:10,used:20,comparable:false}).percent,null)
})
