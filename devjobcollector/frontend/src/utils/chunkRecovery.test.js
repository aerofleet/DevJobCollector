import { test } from 'node:test';
import assert from 'node:assert/strict';
import { recoverChunkFailure } from './chunkRecovery.js';

test('청크 로딩 실패는 한 번 새로고침하고 반복 실패 시 다시 시도하지 않는다', () => {
  const values = new Map();
  const storage = { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value) };
  let reloads = 0, prevented = 0;
  const event = { preventDefault: () => prevented++ };
  assert.equal(recoverChunkFailure(event, storage, () => reloads++, 100_000), true);
  assert.equal(recoverChunkFailure(event, storage, () => reloads++, 100_001), false);
  assert.equal(reloads, 1);
  assert.equal(prevented, 1);
});

test('저장소를 사용할 수 없으면 자동 새로고침하지 않는다', () => {
  assert.equal(recoverChunkFailure({ preventDefault: () => assert.fail() }, { getItem: () => { throw new Error(); } }, () => assert.fail()), false);
});
