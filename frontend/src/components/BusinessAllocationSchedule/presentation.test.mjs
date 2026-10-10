import test from 'node:test'
import assert from 'node:assert/strict'
import { allocationLockLabel, allocationLockHint, allocationBlockedMessage } from './presentation.js'

test('deleted records explain the unavailable repair instead of directing users to closed adjustments',()=>{
  const row={frozen:true,projectDelFlag:'2',accountingState:'OPEN'}
  assert.equal(allocationLockLabel(row),'已删除旧项目')
  assert.match(allocationLockHint(row),/现有调账入口不支持/)
  assert.match(allocationBlockedMessage(row),/已删除/)
})
test('closed accounting and frozen daily results have distinct guidance',()=>{
  assert.equal(allocationLockLabel({frozen:true,accountingState:'CLOSED'}),'已关账')
  assert.match(allocationLockHint({freezeReason:'ACCOUNTING_CLOSED'}),/关账后调整/)
  assert.equal(allocationLockLabel({frozen:true,freezeReason:'PERIOD_CLOSED'}),'该期间含已冻结账目')
  assert.equal(allocationLockLabel({frozen:false}), '')
  assert.match(allocationBlockedMessage({frozen:true}),/该期间含已冻结账目/)
})
