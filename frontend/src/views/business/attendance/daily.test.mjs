import test from 'node:test'
import assert from 'node:assert/strict'
import {dailyAttendance} from './daily.mjs'
test('one day combines sources, preserves multiple punches and excludes old revisions',()=>{
  const base={userId:7,userName:'A',businessDate:'2026-09-04',isCurrent:1,quality:'KNOWN'}
  const rows=[{...base,kind:'ATTENDANCE',detailsJson:JSON.stringify({results:[{checkInTime:1},{checkInTime:2}]})},{...base,kind:'SHIFT',intervalsJson:'[[1,3]]'},{...base,kind:'LEAVE',normalizedStatus:'CONFIRMED',intervalsJson:'[[2,3]]'}, {...base,isCurrent:0,kind:'ATTENDANCE',detailsJson:'{"results":[{"checkInTime":99}]}'}]
  const days=dailyAttendance(rows)
  assert.equal(days.length,1);assert.equal(days[0].punches.length,2);assert.equal(days[0].shifts.length,1);assert.equal(days[0].leaves.length,1)
})
test('people stay separate and missing punches do not become absence or normal',()=>{
  const rows=[{userId:1,businessDate:'2026-09-04',isCurrent:1,kind:'SHIFT',quality:'UNKNOWN'},{userId:2,businessDate:'2026-09-04',isCurrent:1,kind:'REMEDY',normalizedStatus:'CONFIRMED',quality:'KNOWN',detailsJson:'{"remedyTime":"2026-09-04 09:00"}'}]
  const days=dailyAttendance(rows);assert.equal(days.length,2);assert.equal(days[0].punches.length,0);assert.deepEqual(days[0].warnings,['UNKNOWN']);assert.equal(days[1].remedies.length,1)
})
