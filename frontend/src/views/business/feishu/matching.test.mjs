import test from 'node:test'
import assert from 'node:assert/strict'
import { mappingCandidates } from './matching.mjs'
test('ambiguous names on either side never produce an automatic candidate',()=>{
 const base={people:[{userId:1,userName:'Same'}],employees:[{externalUserId:'a',name:'Same'},{externalUserId:'b',name:'Same'}]}
 assert.ok(mappingCandidates(base,'2026-09-01').every(r=>r.ambiguous&&r.userId===null))
 base.employees.pop();base.people.push({userId:2,userName:'Same'})
 assert.equal(mappingCandidates(base,'2026-09-01')[0].userId,null)
})
test('an overlapping binding blocks suggestions including future and end-date boundaries',()=>{
 const base={people:[{userId:1,userName:'One'}],employees:[{externalUserId:'a',name:'One'}],mappings:[{userId:1,externalUserId:'a',effectiveFrom:'2026-09-03',effectiveTo:'2026-09-07'}]}
 assert.ok(mappingCandidates(base,'2026-09-07')[0].bound)
 assert.ok(mappingCandidates(base,'2026-09-01')[0].bound)
 assert.equal(mappingCandidates(base,'2026-09-08')[0].userId,1)
})
test('unavailable staff and accounts already bound to another external identity are not suggested',()=>{
 const base={people:[{userId:1,userName:'One'}],employees:[{externalUserId:'a',name:'One',unavailable:true}]}
 assert.equal(mappingCandidates(base,'2026-09-01')[0].userId,null)
 base.employees[0].unavailable=false;base.mappings=[{userId:1,externalUserId:'other',effectiveFrom:'2026-09-01'}]
 assert.equal(mappingCandidates(base,'2026-09-01')[0].userId,null)
})
