import test from 'node:test'
import assert from 'node:assert/strict'
import { allocationMembers, memberAllocationRow, loadMemberAllocations, memberAllocationSlices } from './memberProjectAllocations.js'

const member = { userId: 7, userName: '蔡新武', participatingProjects: [{ projectId: 1, projectName: 'ins' }] }
const allocation = (projectId, allocationValue, extra = {}) => ({ projectId, projectName: `project-${projectId}`, allocationId: projectId + 100, allocationValue, confirmationStatus: 'CONFIRMED', ...extra })

test('a member participating in two owner projects is counted once; observers, former/future members and closed projects are excluded', () => {
  const participating = { userId: 7, userNameSnapshot: '蔡新武', memberRole: 'MEMBER', joinedDate: '2026-10-01', status: '0' }
  const workspaces = [
    { project: { projectId: 1, projectName: 'ins', status: 'ACTIVE', members: [participating, { ...participating, userId: 8, memberRole: 'OBSERVER' }, { ...participating, userId: 9, leftDate: '2026-10-08' }, { ...participating, userId: 10, joinedDate: '2026-10-10' }] } },
    { project: { projectId: 2, projectName: 'youtube', status: 'PAUSED', members: [{ ...participating, userId: '7', memberRole: 'OWNER' }] } },
    { project: { projectId: 3, status: 'CLOSED', members: [{ ...participating, userId: 11 }] } },
    { project: { projectId: 4, status: 'ACTIVE', accountingState: 'CLOSED', members: [{ ...participating, userId: 12 }] } }
  ]
  const rows = allocationMembers(workspaces, '2026-10-09')
  assert.equal(rows.length, 1)
  assert.equal(rows[0].memberRole, 'OWNER')
  assert.deepEqual(rows[0].participatingProjects.map(row => row.projectName), ['ins', 'youtube'])
})

test('cross-owner allocations are included without renormalizing to the visible owner projects', () => {
  const row = memberAllocationRow(member, { projects: [allocation(1, 30), allocation(2, 70)] }, ['1'])
  assert.equal(row.total, 100)
  assert.equal(row.crossProject, true)
  assert.equal(row.status, 'full')
  assert.equal(row.projects[1].external, true)
  assert.deepEqual(row.projects.map(project => project.percent), [30, 70])
})

test('missing and unconfirmed versions stay unknown; a configured zero remains zero and a pending request does not replace effective weights', () => {
  const pendingRequest = { allocations: [{ projectId: 1, allocationValue: 80 }] }
  const row = memberAllocationRow(member, { projects: [allocation(1, 30), allocation(2, 0), allocation(3, 0, { allocationId: null }), allocation(4, 70, { confirmationStatus: 'PENDING' })], pendingRequest }, [1])
  assert.deepEqual(row.projects.map(project => project.percent), [30, 0, null, null])
  assert.equal(row.total, 30)
  assert.equal(row.status, 'incomplete')
  assert.equal(row.pendingRequest, pendingRequest)
})

test('under and over allocated totals remain visible and projects with the same name are kept separate', () => {
  for (const [values, status, total] of [[[20, 40], 'under', 60], [[70, 60], 'over', 130]]) {
    const row = memberAllocationRow(member, { projects: values.map((value, index) => allocation(index + 1, value, { projectName: '同名项目' })) }, [1, 2])
    assert.equal(row.projects.length, 2)
    assert.equal(row.total, total)
    assert.equal(row.status, status)
  }
})

test('individual failures preserve member participation and do not erase other members or invent 0% totals', async () => {
  const rows = await loadMemberAllocations([member, { ...member, userId: 8 }], [1], async userId => {
    if (userId === 8) throw new Error('unavailable')
    return { projects: [allocation(1, 100)] }
  })
  assert.equal(rows[0].total, 100)
  assert.equal(rows[1].failed, true)
  assert.equal(rows[1].total, null)
  assert.equal(rows[1].participatingProjects[0].projectName, 'ins')
})

test('refreshing or leaving stops scheduling stale member requests, with no more than four requests in flight', async () => {
  let active = 0, maxActive = 0, calls = 0, current = true
  const members = Array.from({ length: 12 }, (_, userId) => ({ ...member, userId }))
  await loadMemberAllocations(members, [1], async () => {
    calls++; active++; maxActive = Math.max(maxActive, active)
    await new Promise(resolve => setTimeout(resolve, 5))
    current = false; active--
    return { projects: [allocation(1, 100)] }
  }, () => current)
  assert.equal(calls, 4)
  assert.equal(maxActive, 4)
})

test('pie charts retain actual percentages and add unallocated space only for incomplete 100% distributions', () => {
  const under = memberAllocationRow(member, { projects: [allocation(1, 30), allocation(2, 0)] }, [1])
  assert.deepEqual(memberAllocationSlices(under).map(slice => [slice.kind, slice.value]), [['project', 30], ['unallocated', 70]])
  const over = memberAllocationRow(member, { projects: [allocation(1, 70), allocation(2, 60)] }, [1])
  assert.deepEqual(memberAllocationSlices(over).map(slice => slice.value), [70, 60])
  assert.equal(over.total, 130)
  const unknown = memberAllocationRow(member, { projects: [allocation(1, 30), allocation(2, 0, { allocationId: null })] }, [1])
  assert.equal(memberAllocationSlices(unknown).at(-1).kind, 'unknown')
  assert.deepEqual(memberAllocationSlices({ projects: [], failed: true }).map(slice => slice.kind), ['unknown'])
})
