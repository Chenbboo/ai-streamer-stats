/**
 * Format a Date as the calendar date seen by the current user.
 * Avoid Date#toISOString here: it converts to UTC and can move the date back a day.
 */
export function formatLocalDate(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function todayLocal() {
  return formatLocalDate(new Date())
}
