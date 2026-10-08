import { test } from 'node:test'
import assert from 'node:assert/strict'
import { flattenProjectRows } from './projectHierarchy.js'
const roots=[{projectId:1,children:[{projectId:10,parentId:1,children:[]}]},{projectId:2,children:[]}]
test('defaults to roots and expanding one root preserves independent state',()=>{
 assert.deepEqual(flattenProjectRows(roots,new Set()).map(r=>r.projectId),[1,2])
 assert.deepEqual(flattenProjectRows(roots,new Set([1,2])).map(r=>r.projectId),[1,10,2,'empty-2'])
 assert.deepEqual(flattenProjectRows(roots,new Set([2])).map(r=>r.projectId),[1,2,'empty-2'])
})
test('child rows do not affect the supplied root page and carry indentation',()=>{
 const rows=flattenProjectRows(roots.slice(0,1),new Set([1]))
 assert.equal(rows.length,2);assert.equal(rows[1].depth,1);assert.equal(roots.length,2)
})
test('loading and failure placeholders remain bound to the correct parent',()=>{
 const loading=flattenProjectRows([{projectId:1,children:[],childLoading:true}],new Set([1]))[1]
 assert.equal(loading.parentId,1);assert.equal(loading.childLoading,true)
 const failed=flattenProjectRows([{projectId:2,children:[],childError:true}],new Set([2]))[1]
 assert.equal(failed.parentId,2);assert.equal(failed.childError,true)
})
