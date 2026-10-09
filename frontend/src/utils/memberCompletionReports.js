export function filterMemberCompletionReports(records, { memberUserId, projectId, dates } = {}) {
  return records.filter(row =>
    (memberUserId == null || memberUserId === '' || String(row.memberUserId) === String(memberUserId)) &&
    (projectId == null || projectId === '' || String(row.projectId) === String(projectId)) &&
    (!dates?.[0] || row.reportDate >= dates[0]) &&
    (!dates?.[1] || row.reportDate <= dates[1])
  ).sort((left, right) =>
    String(right.reportDate).localeCompare(String(left.reportDate)) ||
    String(right.submittedTime || '').localeCompare(String(left.submittedTime || '')) ||
    Number(right.submissionId || 0) - Number(left.submissionId || 0) ||
    String(left.workType).localeCompare(String(right.workType)) || Number(right.reportId) - Number(left.reportId)
  )
}

export function mergeMemberCompletionReports(workspace = {}) {
  const project = workspace.project || {}
  const reports = (workspace.workReports || []).map(report => ({
    workType: 'WORK_REPORT', reportId: report.reportId,
    projectId: report.projectId ?? project.projectId,
    projectName: report.projectName || project.projectName,
    memberUserId: report.submittedUserId, memberName: report.submittedUserName,
    workName: report.routineName || '', routineId: report.routineId,
    reportDate: String(report.createTime || report.periodEnd || report.periodStart || '').slice(0, 10),
    submittedTime: report.createTime, frequency: report.frequency,
    periodStart: report.periodStart, periodEnd: report.periodEnd,
    reportDetails: report.content, evidenceUrls: report.attachmentUrls
  }))
  return [...(workspace.memberCompletionReports || []), ...reports]
}
