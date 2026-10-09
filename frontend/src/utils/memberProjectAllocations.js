const activeStatuses = new Set(['PLANNING', 'ACTIVE', 'PAUSED', 'ACCEPTANCE'])
const memberRoles = new Set(['OWNER', 'DEPUTY', 'MEMBER'])
const day = value => value ? String(value).slice(0, 10) : ''
const id = value => value == null ? '' : String(value)
const colors = ['#4087ef', '#25a998', '#8871d9', '#e6a23c', '#d76687', '#389dbb', '#749341', '#aa7654']

export function allocationProjectColor(projectId) {
  let hash = 0
  for (const char of id(projectId)) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return colors[hash % colors.length]
}

/** Seed members once across the owner's current projects; spectators do not carry an allocation. */
export function allocationMembers(workspaces, date) {
  const members = new Map()
  for (const workspace of workspaces || []) {
    const project = workspace.project || {}
    if (!activeStatuses.has(project.status) || project.accountingState === 'CLOSED') continue
    for (const member of project.members || []) {
      if (!id(member.userId) || String(member.status ?? '0') !== '0' || !memberRoles.has(member.memberRole)) continue
      if (day(member.joinedDate) > date || day(member.leftDate) && day(member.leftDate) < date) continue
      const key = id(member.userId)
      if (!members.has(key)) members.set(key, {
        userId: member.userId,
        userName: member.userNameSnapshot || member.accountName || key,
        accountName: member.accountName || '',
        memberRole: member.memberRole,
        participatingProjects: []
      })
      const row = members.get(key)
      const roles = ['OWNER', 'DEPUTY', 'MEMBER']
      if (roles.indexOf(member.memberRole) < roles.indexOf(row.memberRole)) row.memberRole = member.memberRole
      if (!row.participatingProjects.some(item => id(item.projectId) === id(project.projectId))) {
        row.participatingProjects.push({ projectId: project.projectId, projectName: project.projectName })
      }
    }
  }
  return [...members.values()]
}

/** Keep the service's dated cross-project weights; never normalize or substitute missing values. */
export function memberAllocationRow(member, workspace, ownedProjectIds) {
  const owned = new Set((ownedProjectIds || []).map(id))
  const projects = (workspace.projects || []).map(project => {
    const value = project.allocationValue
    const configured = project.allocationId != null && value != null && value !== '' && Number.isFinite(Number(value)) && Number(value) >= 0
    const pending = project.confirmationStatus === 'PENDING'
    return {
      ...project,
      percent: configured && !pending ? Number(value) : null,
      pending,
      external: !owned.has(id(project.projectId)),
      color: allocationProjectColor(project.projectId)
    }
  })
  const total = Math.round(projects.reduce((sum, project) => sum + (project.percent ?? 0), 0) * 100) / 100
  const incomplete = projects.some(project => project.percent === null)
  return {
    ...member,
    projects,
    total,
    incomplete,
    crossProject: projects.length > 1,
    pendingRequest: workspace.pendingRequest || null,
    status: !projects.length ? 'empty' : incomplete ? 'incomplete' : total > 100.01 ? 'over' : Math.abs(total - 100) <= 0.01 ? 'full' : 'under',
    failed: false
  }
}

/** Pie geometry has a 100% reference; tooltips must use these actual values, never ECharts' normalized percent. */
export function memberAllocationSlices(member) {
  const slices = (member.projects || []).filter(project => project.percent > 0).map(project => ({
    kind: 'project', projectId: project.projectId, name: project.projectName, value: project.percent, color: project.color
  }))
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  if (total < 100) slices.push({ kind: member.failed || member.incomplete || member.status === 'empty' ? 'unknown' : 'unallocated', value: 100 - total, color: '#e9eef5' })
  return slices
}

/** Bound concurrency for large teams and preserve partial failures without hiding members. */
export async function loadMemberAllocations(members, ownedProjectIds, fetchWorkspace, isCurrent = () => true) {
  const rows = new Array(members.length)
  let next = 0
  await Promise.all(Array.from({ length: Math.min(4, members.length) }, async () => {
    while (next < members.length && isCurrent()) {
      const index = next++
      const member = members[index]
      try {
        rows[index] = memberAllocationRow(member, await fetchWorkspace(member.userId), ownedProjectIds)
      } catch (error) {
        rows[index] = { ...member, projects: [], total: null, crossProject: member.participatingProjects.length > 1, failed: true, error: error?.message || '' }
      }
    }
  }))
  return rows.filter(Boolean)
}
