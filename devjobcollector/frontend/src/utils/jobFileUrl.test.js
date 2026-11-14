import test from 'node:test';
import assert from 'node:assert/strict';
import { canPreviewJobFile, getPreviewType, jobFileUrl } from './jobFileUrl.js';

test('only the exact legacy ALIO file URL maps to its official download', () => {
  assert.equal(jobFileUrl('https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=3085641'), 'https://www.alio.go.kr/download/download.json?fileNo=3085641');
  assert.equal(jobFileUrl('javascript:alert(1)'), null);
  assert.equal(jobFileUrl('https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=1&other=2'), 'https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=1&other=2');
});

test('preview button requires an allowed HTTPS host and supported extension', () => {
  assert.equal(canPreviewJobFile({ fileName: '공고.HWPX', fileUrl: 'https://www.alio.go.kr/a' }), true);
  for (const fileUrl of ['http://www.alio.go.kr/a', 'https://www.alio.go.kr.evil.example/a', 'https://evil.example/a', 'https://www.alio.go.kr:8443/a'])
    assert.equal(canPreviewJobFile({ fileName: '공고.hwp', fileUrl }), false);
  for (const ext of ['pdf', 'png', 'jpg', 'jpeg', 'webp'])
    assert.equal(canPreviewJobFile({ fileName: `공고.${ext}`, fileUrl: 'https://www.alio.go.kr/a' }), true);
  assert.equal(getPreviewType({ fileName: '공고.PDF' }), 'PDF');
  assert.equal(canPreviewJobFile({ fileName: '공고.svg', fileUrl: 'https://www.alio.go.kr/a' }), false);
});
