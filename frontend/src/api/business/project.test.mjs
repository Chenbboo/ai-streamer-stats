import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

// Exercise the real API wrapper without Axios or a live user session.
const source = readFileSync(new URL('./project.js', import.meta.url), 'utf8')
const moduleSource = source.replace(/import request from ['"]@\/utils\/request['"]/, 'const request = options => options')
const { getBusinessOwnerSpendHistory } = await import(`data:text/javascript;base64,${Buffer.from(moduleSource).toString('base64')}`)

test('owner spend history uses the controller business/owner route, not business/project/owner', () => {
  assert.deepEqual(getBusinessOwnerSpendHistory(13, { month: '2026-09' }), {
    url: '/business/owner/spend-history/13', method: 'get', params: { month: '2026-09' }
  })
})
test('owner spend history forwards a single date to the same route', () => {
  assert.deepEqual(getBusinessOwnerSpendHistory(13, { bizDate: '2026-09-28' }), {
    url: '/business/owner/spend-history/13', method: 'get', params: { bizDate: '2026-09-28' }
  })
})
