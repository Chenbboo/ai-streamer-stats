import test from 'node:test'
import assert from 'node:assert/strict'
import { compressReportImage } from './compressReportImage.js'

const fileOf = (name, size) => new File([new Uint8Array(size)], name, { type: 'image/png' })

function withCanvas(outputSize, run) {
  const previousBitmap = globalThis.createImageBitmap
  const previousDocument = globalThis.document
  let closed = false
  let canvas
  globalThis.createImageBitmap = async () => ({ width: 4000, height: 2000, close: () => { closed = true } })
  globalThis.document = {
    createElement: () => {
      canvas = {
        width: 0,
        height: 0,
        getContext: () => ({ drawImage() {}, imageSmoothingEnabled: false, imageSmoothingQuality: '' }),
        toBlob: callback => callback(new Blob([new Uint8Array(outputSize)], { type: 'image/webp' }))
      }
      return canvas
    }
  }
  return Promise.resolve().then(() => run(() => ({ closed, canvas }))).finally(() => {
    globalThis.createImageBitmap = previousBitmap
    globalThis.document = previousDocument
  })
}

test('compresses a large report image and keeps extension, type and dimensions consistent', async () => {
  await withCanvas(100_000, async inspect => {
    const original = fileOf('工作截图.png', 1_000_000)
    const result = await compressReportImage(original)
    assert.equal(result.compressed, true)
    assert.equal(result.file.name, '工作截图.webp')
    assert.equal(result.file.type, 'image/webp')
    assert.equal(result.file.size, 100_000)
    assert.equal(result.originalSize, 1_000_000)
    assert.equal(inspect().canvas.width, 2560)
    assert.equal(inspect().canvas.height, 1280)
    assert.equal(inspect().closed, true)
  })
})

test('retains the original when WebP offers no meaningful saving', async () => {
  await withCanvas(98_000, async inspect => {
    const original = fileOf('small.png', 100_000)
    const result = await compressReportImage(original)
    assert.equal(result.file, original)
    assert.equal(result.compressed, false)
    assert.equal(inspect().closed, true)
  })
})

test('leaves non-image attachments unchanged', async () => {
  const original = fileOf('report.pdf', 100_000)
  const result = await compressReportImage(original)
  assert.equal(result.file, original)
  assert.equal(result.compressed, false)
})
