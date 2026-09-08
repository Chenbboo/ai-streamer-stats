// Suggestions never select rows or establish an identity without administrator confirmation.
export function mappingCandidates(snapshot, effectiveFrom) {
 const maps=(snapshot.mappings||[]).filter(m=>!m.effectiveTo||String(m.effectiveTo).slice(0,10)>=effectiveFrom)
 const employees=snapshot.employees||[],people=snapshot.people||[]
 return employees.map(e=>{
  const bound=maps.find(m=>m.externalUserId===e.externalUserId)
  const candidates=people.filter(p=>p.userName?.trim()===e.name?.trim()&&!maps.some(m=>m.userId===p.userId))
  const ambiguous=candidates.length>1||employees.filter(other=>other.name?.trim()===e.name?.trim()).length>1
  const suggested=!e.unavailable&&!bound&&!ambiguous&&candidates.length===1
  return {...e,bound,ambiguous,suggested,userId:suggested?candidates[0].userId:null}
 })
}
