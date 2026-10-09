import test from 'node:test'
import assert from 'node:assert/strict'
import {dailyAttendance,matchesAttendanceStatus} from './daily.mjs'
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
test('abnormal filter retains employee dates and matches any punch across split shifts',()=>{
  const base={businessDate:'2026-09-04',isCurrent:1,quality:'KNOWN',kind:'ATTENDANCE'}
  const source=(userId,date,results)=>({...base,userId,businessDate:date,detailsJson:JSON.stringify({results})})
  const rows=[
    source(7,'2026-09-04',[{checkInResult:'Normal',checkOutResult:'Normal'},{checkInResult:'Late',checkOutResult:'Early'}]),
    source(7,'2026-09-05',[{checkInResult:'Normal',checkOutResult:'Normal'}]),
    source(8,'2026-09-04',[{checkInResult:'Lack',checkOutResult:'Normal'}]),
    {...source(7,'2026-09-05',[{checkInResult:'Late',checkOutResult:'Normal'}]),isCurrent:0}
  ]
  const days=dailyAttendance(rows),matching=status=>days.filter(day=>matchesAttendanceStatus(day,status)).map(day=>day.key)
  assert.deepEqual(matching('ALL'),['7:2026-09-05','7:2026-09-04','8:2026-09-04'])
  assert.deepEqual(matching('ABNORMAL'),['7:2026-09-04','8:2026-09-04'])
  assert.deepEqual(matching('Late'),['7:2026-09-04'])
  assert.deepEqual(matching('Early'),['7:2026-09-04'])
  assert.deepEqual(matching('Lack'),['8:2026-09-04'])
})
test('pending punches, missing data and freshness warnings do not imply an abnormal result',()=>{
  for(const checkOutResult of ['Todo','UNKNOWN',undefined,'NoNeedCheck','SystemCheck','Normal']){
    const day={punches:[{checkInResult:'Normal',checkOutResult}],warnings:['STALE']}
    assert.equal(matchesAttendanceStatus(day,'ABNORMAL'),false)
    assert.equal(matchesAttendanceStatus(day,'Todo'),checkOutResult==='Todo')
  }
  assert.equal(matchesAttendanceStatus({punches:[],warnings:['UNKNOWN']},'ABNORMAL'),false)
  assert.equal(matchesAttendanceStatus({punches:[{checkInResult:'Late',checkOutResult:'Todo'}],warnings:[]},'ABNORMAL'),true)
})
test('approved and withdrawn outings accompany original missing-punch results without becoming leave',()=>{
  const base={userId:137,userName:'陈嘉雪',businessDate:'2026-09-17',isCurrent:1,quality:'KNOWN',sourceTimezone:'Asia/Shanghai'}
  const days=dailyAttendance([
    {...base,kind:'ATTENDANCE',detailsJson:JSON.stringify({results:[{checkInResult:'Lack',checkOutResult:'Normal',checkInTime:1789610334,checkOutTime:1789639561}]})},
    {...base,kind:'OUT',normalizedStatus:'CONFIRMED',sourceDurationSeconds:3600,intervalsJson:'[[1789606800,1789610400]]'},
    {...base,kind:'OUT',normalizedStatus:'CANCELED',sourceDurationSeconds:3600,intervalsJson:'[[1789606800,1789610400]]'},
    {...base,kind:'OUT',isCurrent:0,normalizedStatus:'CONFIRMED',intervalsJson:'[[1,2]]'}
  ])
  assert.equal(days.length,1)
  assert.deepEqual(days[0].outings.map(p=>p.status),['CONFIRMED','CANCELED'])
  assert.deepEqual(days[0].outings[0],{status:'CONFIRMED',seconds:3600,intervals:[[1789606800,1789610400]],zone:'Asia/Shanghai'})
  assert.equal(days[0].leaves.length,0)
  assert.equal(days[0].punches[0].checkInResult,'Lack')
  assert.equal(matchesAttendanceStatus(days[0],'Lack'),true)
})
test('outings remain visible on no-punch-required days and preserve cross-day times and source warnings',()=>{
  const base={userId:7,businessDate:'2026-09-07',isCurrent:1,quality:'KNOWN'}
  const punch={...base,kind:'ATTENDANCE',detailsJson:'{"results":[{"checkInResult":"NoNeedCheck","checkOutResult":"NoNeedCheck"}]}'}
  assert.equal(dailyAttendance([punch]).length,0)
  const days=dailyAttendance([punch,{...base,kind:'OUT',quality:'STALE',normalizedStatus:'CONFIRMED',sourceTimezone:'Asia/Ho_Chi_Minh',intervalsJson:'[[1788796800,1788804000]]'}])
  assert.equal(days.length,1)
  assert.equal(days[0].outings[0].zone,'Asia/Ho_Chi_Minh')
  assert.deepEqual(days[0].warnings,['STALE'])
  assert.equal(matchesAttendanceStatus(days[0],'ABNORMAL'),false)
})
