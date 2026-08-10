import assert from 'node:assert/strict';
import test from 'node:test';
import { validateProxyUrl } from './proxySecurity';

test('accepts an allowed HTTPS provider', () => {
  const url = validateProxyUrl('https://cdn1.ducvomes.com/video/master.m3u8');
  assert.equal(url.hostname, 'cdn1.ducvomes.com');
});

test('rejects local and unapproved targets', () => {
  assert.throws(() => validateProxyUrl('http://127.0.0.1:3000/private'));
  assert.throws(() => validateProxyUrl('https://example.com/video.m3u8'));
});
