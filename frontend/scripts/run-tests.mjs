import { readdirSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
function discover(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? discover(path) : /\.test\.(mjs|js)$/.test(entry.name) ? [path] : []
  })
}

const files = discover(join(root, 'src')).sort()
if (!files.length) {
  console.error('No frontend test files found.')
  process.exit(1)
}
console.log(`Running ${files.length} frontend test files.`)
const result = spawnSync(process.execPath, ['--test', ...files], { cwd: root, stdio: 'inherit' })
if (result.error) console.error(result.error.message)
process.exit(result.status ?? 1)
