import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { validateStorefront } from '../check-storefront.mjs';

const original = JSON.parse(fs.readFileSync(new URL('../data/storefront.json', import.meta.url), 'utf8'));

test('data rejects ambiguous titles, broken references and invalid stock', () => {
  validateStorefront(original);
  const duplicate = structuredClone(original);
  duplicate.products.push(duplicate.products[0]);
  assert.throws(() => validateStorefront(duplicate), /重复/);
  const broken = structuredClone(original);
  broken.collections[0].productTitles.push('missing product');
  assert.throws(() => validateStorefront(broken), /不存在的商品/);
  const stock = structuredClone(original);
  stock.defaults.stockByStore['test'] = -1;
  assert.throws(() => validateStorefront(stock), /库存/);
});

test('collection errors produce a failing process status', async () => {
  const requests = [];
  const server = http.createServer((req, res) => {
    requests.push(req.url);
    let data;
    if (req.url === '/api/admin/auth/login') data = { token: 'test', staff: { username: 'ci', role: 'ADMIN' } };
    else if (req.url === '/api/admin/stores') data = [];
    else if (req.url.startsWith('/api/admin/products?')) {
      data = { list: [{ id: 1, title: new URL(req.url, 'http://localhost').searchParams.get('keyword') }] };
    } else if (req.method === 'GET' && req.url === '/api/admin/collections') data = [];
    else {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ code: 50000, message: 'old backend' }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ code: 0, data }));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const result = await new Promise((resolve, reject) => {
      const child = spawn(process.execPath, [new URL('../seed-storefront.mjs', import.meta.url).pathname, 'collections'], {
        env: { ...process.env, XC_BASE: `http://127.0.0.1:${server.address().port}`, XC_USER: 'ci', XC_PASS: 'test' },
      });
      let output = '';
      child.stdout.on('data', (chunk) => { output += chunk; });
      child.stderr.on('data', (chunk) => { output += chunk; });
      child.on('error', reject);
      child.on('close', (code) => resolve({ code, output }));
    });
    assert.equal(result.code, 1, result.output);
    assert.match(result.output, /系列录入失败/);
    assert.doesNotMatch(result.output, /完成。/);
    assert(!requests.some((url) => url.includes('home-themes')));
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
