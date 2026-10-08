import test from 'node:test'
import assert from 'node:assert/strict'
import { createRouteLoadErrorHandler, isRouteAssetError } from './routeLoadError.js'
import zh from '../locales/zh-CN.js'
import vi from '../locales/vi-VN.js'

const missingChunk = new TypeError('Failed to fetch dynamically imported module: /static/js/old-page.js')

function fixture(overrides = {}) {
  const entries = new Map()
  const events = []
  const options = {
    finishProgress: () => events.push('done'),
    notify: key => events.push(['notify', key]),
    reload: target => events.push(['reload', target]),
    storage: { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) },
    now: () => 1000,
    ...overrides
  }
  return { options, events, handle: createRouteLoadErrorHandler(options) }
}

test('a missing lazy chunk reloads the requested page including query and hash', () => {
  const { handle, events } = fixture()
  handle(missingChunk, { fullPath: '/jewelry/document?page=2#details' })
  assert.deepEqual(events, ['done', ['reload', '/jewelry/document?page=2#details']])
})

test('a new app instance retains the retry limit and displays a useful error', () => {
  const { handle, events, options } = fixture()
  const to = { fullPath: '/jewelry/stock' }
  handle(missingChunk, to)
  createRouteLoadErrorHandler(options)(missingChunk, to)
  assert.deepEqual(events, ['done', ['reload', to.fullPath], 'done', ['notify', 'navigation.assetLoadFailed']])
})

test('a later outage can recover after the retry interval', () => {
  const { handle, events, options } = fixture()
  const to = { fullPath: '/business/projects' }
  handle(missingChunk, to)
  createRouteLoadErrorHandler({ ...options, now: () => 61000 })(missingChunk, to)
  assert.equal(events.filter(event => Array.isArray(event) && event[0] === 'reload').length, 2)
})

test('storage failures do not cause a reload loop or suppress the error message', () => {
  for (const storage of [
    { getItem() { throw new Error('storage disabled') } },
    { getItem: () => null, setItem() { throw new Error('storage full') } },
    { getItem: () => '{invalid' }
  ]) {
    const { handle, events } = fixture({ storage })
    handle(missingChunk, { fullPath: '/index' })
    assert.deepEqual(events, ['done', ['notify', 'navigation.assetLoadFailed']])
  }
})

test('unrelated errors stop progress and show an error without reloading', () => {
  const { handle, events } = fixture()
  handle(new Error('Cannot read properties of undefined'), { fullPath: '/index' })
  assert.deepEqual(events, ['done', ['notify', 'navigation.failed']])
})

test('asset failures support browser variants and CSS preload failures', () => {
  for (const message of [
    'error loading dynamically imported module: /page.js',
    'Importing a module script failed.',
    'Unable to preload CSS for /static/css/page.css',
    'Loading chunk 12 failed.',
    'Loading CSS chunk page failed.'
  ]) assert.equal(isRouteAssetError(new Error(message)), true, message)
  assert.equal(isRouteAssetError(null), false)
})

test('missing or external targets cannot trigger an automatic navigation', () => {
  for (const fullPath of [undefined, 'https://example.com', '//example.com']) {
    const { handle, events } = fixture()
    handle(missingChunk, { fullPath })
    assert.deepEqual(events, ['done', ['notify', 'navigation.assetLoadFailed']])
  }
})

test('both supported languages provide the recovery messages', () => {
  for (const messages of [zh, vi]) {
    assert.ok(messages.navigation.assetLoadFailed)
    assert.ok(messages.navigation.failed)
  }
})
