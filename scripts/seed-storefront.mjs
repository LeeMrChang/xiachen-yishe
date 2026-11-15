#!/usr/bin/env node
/**
 * 夏晨衣舍 · 门店数据录入脚本
 *
 * 把 scripts/data/storefront.json 里的商品、造型、首页主题通过后台 API 写入。
 * 全程按「标题 / 名称」查找匹配，不依赖数据库 id，所以同一份数据可以在本地和生产重复执行。
 *
 * 用法:
 *   XC_USER=admin XC_PASS=****** node scripts/seed-storefront.mjs products
 *   XC_USER=admin XC_PASS=****** node scripts/seed-storefront.mjs all --activate
 *
 * 子命令:
 *   products    商品 + SKU + 图片 + 三店库存 + 上架
 *   collections 系列 + 系列内商品 + 上线
 *   looks       造型 + 单品组合 + 发布
 *   theme      首页主题 + 模块（--activate 设为当前主题）
 *   cleanup    下线 db/demo 种子数据（商品 / 造型 / 主题）
 *   all        products → collections → looks → theme
 *
 * 环境变量:
 *   XC_BASE      后台 API 地址，默认 http://127.0.0.1:8082
 *   XC_USER      后台账号（必填）
 *   XC_PASS      后台密码（必填）
 *   XC_IMG_DIR   商品图目录，默认 ~/Downloads
 *
 * 安全: XC_BASE 不是本机时必须加 --yes，避免误操作生产。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateStorefront } from './check-storefront.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA = JSON.parse(fs.readFileSync(path.join(__dirname, 'data/storefront.json'), 'utf8'));
validateStorefront(DATA);

const BASE = (process.env.XC_BASE || 'http://127.0.0.1:8082').replace(/\/$/, '');
const USER = process.env.XC_USER;
const PASS = process.env.XC_PASS;
const IMG_DIR = process.env.XC_IMG_DIR || path.join(os.homedir(), 'Downloads');

const argv = process.argv.slice(2);
const cmd = argv.find((a) => !a.startsWith('-')) || 'all';
const DRY = argv.includes('--dry');
const ACTIVATE = argv.includes('--activate');
const YES = argv.includes('--yes');
const onlyArg = argv.find((a) => a.startsWith('--only='));
const ONLY = onlyArg ? Number(onlyArg.split('=')[1]) : Infinity;

const isLocal = /^https?:\/\/(127\.0\.0\.1|localhost)(:|\/|$)/.test(BASE);

let TOKEN = '';
let STORES = [];

// ---------- HTTP ----------
async function api(method, url, body) {
  const isLogin = url.startsWith('/api/admin/auth/login');
  if (DRY && method !== 'GET' && !isLogin) { console.log(`   [dry] ${method} ${url}`); return {}; }
  const headers = { Authorization: 'Bearer ' + TOKEN };
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch(BASE + url, { method, headers, body: payload });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { throw new Error(`${method} ${url} → HTTP ${res.status}: ${text.slice(0, 200)}`); }
  if (json.code !== 0) throw new Error(`${method} ${url} → code=${json.code} ${json.message}`);
  return json.data;
}

// ---------- 查找助手（按标题/名称，不靠 id）----------
async function findProduct(title) {
  const page = await api('GET', `/api/admin/products?keyword=${encodeURIComponent(title)}&page=1&pageSize=100`);
  return (page.list || []).find((r) => r.title === title) || null;
}
async function findLook(title) {
  const page = await api('GET', `/api/admin/looks?keyword=${encodeURIComponent(title)}&page=1&pageSize=100`);
  return (page.list || []).find((r) => r.title === title) || null;
}
async function findTheme(name) {
  return (await api('GET', '/api/admin/home-themes')).find((t) => t.name === name) || null;
}
async function findCollection(name) {
  return (await api('GET', '/api/admin/collections')).find((c) => c.name === name) || null;
}
function findStore(name) {
  return STORES.find((s) => s.name === name) || null;
}

// ---------- 步骤 1：商品 ----------
async function stepProducts() {
  console.log('\n═══ 商品 ═══');
  const stockMap = DATA.defaults.stockByStore;
  let created = 0, updated = 0, skipped = 0;

  for (const [i, p] of DATA.products.entries()) {
    if (i >= ONLY) break;
    const filePath = path.join(IMG_DIR, p.file);
    try {
      let product = await findProduct(p.title);
      let id;
      if (product) {
        id = product.id;
        await api('PUT', `/api/admin/products/${id}`, {
          title: p.title, subtitle: p.subtitle, category: p.category, season: p.season,
          material: null, description: null, sortWeight: p.sortWeight,
        });
        updated++;
        process.stdout.write(`↻ ${p.title}\n`);
      } else {
        const made = await api('POST', '/api/admin/products', {
          title: p.title, subtitle: p.subtitle, category: p.category, season: p.season,
          material: null, description: null, sortWeight: p.sortWeight,
        });
        id = made.id;
        created++;
        process.stdout.write(`+ ${p.title}  id=${id}\n`);
      }

      // SKU
      const items = [];
      for (const color of p.colors) {
        for (const size of p.sizes) {
          items.push({ color, size, priceCent: Math.round(p.price * 100), originPriceCent: Math.round(p.origin * 100) });
        }
      }
      const withSkus = await api('PUT', `/api/admin/products/${id}/skus`, { items });
      const skus = withSkus.skus || [];

      // 图片：已有就直接用，不再重复上传
      let coverUrl = withSkus.coverUrl;
      if (!coverUrl) {
        if (!fs.existsSync(filePath)) throw new Error(`图片不存在: ${filePath}`);
        const fd = new FormData();
        fd.append('file', new Blob([fs.readFileSync(filePath)], { type: 'image/jpeg' }), path.basename(p.file));
        const up = await api('POST', '/api/admin/uploads', fd);
        const bound = await api('POST', `/api/admin/products/${id}/images`, { url: up.url, width: up.width, height: up.height });
        const imgs = bound.images || [];
        const newest = imgs[imgs.length - 1] || imgs[0];
        if (newest?.id) await api('POST', `/api/admin/products/${id}/images/${newest.id}/cover`);
        coverUrl = up.url;
      }

      // 三店库存
      const inv = [];
      for (const sku of skus) {
        for (const st of STORES) {
          inv.push({ skuId: sku.id, storeId: st.id, quantity: stockMap[st.name] ?? 5, onSale: true });
        }
      }
      if (inv.length) await api('PUT', '/api/admin/inventory', { items: inv });

      await api('POST', `/api/admin/products/${id}/publish`);
      console.log(`    sku=${skus.length} 库存格=${inv.length}`);
    } catch (e) {
      skipped++;
      console.log(`❌ ${p.title}: ${e.message}`);
    }
  }
  console.log(`商品：新增 ${created} / 更新 ${updated} / 失败 ${skipped}`);
  if (skipped) throw new Error(`${skipped} 个商品录入失败，停止后续步骤`);
}

// ---------- 步骤 2：系列 ----------
async function stepCollections() {
  console.log('\n═══ 系列 ═══');
  const list = DATA.collections || [];
  if (list.length === 0) { console.log('（storefront.json 未配置 collections，跳过）'); return; }
  let ok = 0, fail = 0;

  for (const col of list) {
    try {
      const ids = [];
      for (const title of col.productTitles || []) {
        const p = await findProduct(title);
        if (!p) throw new Error(`找不到商品「${title}」，先跑 products`);
        ids.push(p.id);
      }

      let existing = null;
      try {
        existing = (await api('GET', '/api/admin/collections')).find((c) => c.name === col.name) || null;
      } catch {
        throw new Error('当前后台还没有系列接口，请先部署最新 xc-admin');
      }

      const body = {
        name: col.name, story: col.story || '', coverUrl: existing?.coverUrl || '',
        publishAt: existing?.publishAt || null, status: existing?.status || 'DRAFT',
        sortNo: col.sortNo ?? 0,
      };

      let id;
      if (existing) {
        id = existing.id;
        await api('PUT', `/api/admin/collections/${id}`, body);
        process.stdout.write(`↻ ${col.name}`);
      } else {
        const made = await api('POST', '/api/admin/collections', body);
        id = made.id;
        process.stdout.write(`+ ${col.name}`);
      }

      await api('PUT', `/api/admin/collections/${id}/items`, { productIds: ids });
      await api('POST', `/api/admin/collections/${id}/publish`);
      console.log(`  id=${id} 商品=${ids.length}`);
      ok++;
    } catch (e) {
      console.log(`❌ ${col.name}: ${e.message}`);
      fail++;
    }
  }
  console.log(`系列：成功 ${ok} / 失败 ${fail}`);
  if (fail) throw new Error(`${fail} 个系列录入失败，停止后续步骤`);
}

// ---------- 步骤 2：造型 ----------
async function stepLooks() {
  console.log('\n═══ 造型 ═══');
  let ok = 0, fail = 0;
  for (const lk of DATA.looks) {
    try {
      const ids = [];
      for (const title of lk.products) {
        const p = await findProduct(title);
        if (!p) throw new Error(`找不到商品「${title}」，先跑 products`);
        ids.push(p.id);
      }
      const cover = await findProduct(lk.coverProduct);
      if (!cover) throw new Error(`找不到封面商品「${lk.coverProduct}」`);

      let look = await findLook(lk.title);
      let id;
      if (look) {
        id = look.id;
        await api('PUT', `/api/admin/looks/${id}`, {
          title: lk.title, description: lk.description, coverUrl: cover.coverUrl,
          coverWidth: cover.coverWidth || 1080, coverHeight: cover.coverHeight || 1440, sortNo: lk.sortNo,
        });
        process.stdout.write(`↻ ${lk.title}`);
      } else {
        const made = await api('POST', '/api/admin/looks', {
          title: lk.title, description: lk.description, coverUrl: cover.coverUrl,
          coverWidth: cover.coverWidth || 1080, coverHeight: cover.coverHeight || 1440, sortNo: lk.sortNo,
        });
        id = made.id;
        process.stdout.write(`+ ${lk.title}`);
      }
      await api('PUT', `/api/admin/looks/${id}/items`, { productIds: ids });
      await api('POST', `/api/admin/looks/${id}/publish`);
      console.log(`  id=${id} 单品=${ids.length}`);
      ok++;
    } catch (e) {
      console.log(`❌ ${lk.title}: ${e.message}`);
      fail++;
    }
  }
  console.log(`造型：成功 ${ok} / 失败 ${fail}`);
  if (fail) throw new Error(`${fail} 个造型录入失败，停止后续步骤`);
}

// ---------- 步骤 3：首页主题 ----------
async function stepTheme() {
  console.log('\n═══ 首页主题 ═══');
  const t = DATA.theme;
  const cover = await findProduct(t.coverProduct);
  if (!cover) throw new Error(`找不到封面商品「${t.coverProduct}」，先跑 products`);

  let theme = await findTheme(t.name);
  let id;
  if (theme) {
    id = theme.id;
    await api('PUT', `/api/admin/home-themes/${id}`, {
      name: t.name, copyText: t.copyText, coverUrl: cover.coverUrl,
      startAt: null, endAt: null, status: 'ON',
    });
    console.log(`↻ 主题「${t.name}」id=${id}`);
  } else {
    const made = await api('POST', '/api/admin/home-themes', {
      name: t.name, copyText: t.copyText, coverUrl: cover.coverUrl,
      startAt: null, endAt: null, status: 'ON',
    });
    id = made.id;
    console.log(`+ 主题「${t.name}」id=${id}`);
  }

  // 模块：按类型逐个解析引用；解析不到的可选模块跳过并告警
  const modules = [];
  for (const m of t.modules) {
    if (m.type === 'COLLECTION') {
      const col = await findCollection(m.collectionName);
      if (!col) {
        if (m.optional) { console.log(`⚠️  跳过「系列」模块：「${m.collectionName}」不存在`); continue; }
        throw new Error(`系列「${m.collectionName}」不存在`);
      }
      modules.push({ type: 'COLLECTION', refId: col.id, enabled: true });
    } else if (m.type === 'STORE') {
      const st = findStore(m.storeName);
      if (!st) throw new Error(`门店「${m.storeName}」不存在`);
      modules.push({ type: 'STORE', refId: st.id, enabled: true });
    } else if (m.type === 'BANNER') {
      const img = m.imageProduct ? await findProduct(m.imageProduct) : null;
      modules.push({ type: 'BANNER', imageUrl: img?.coverUrl || '', enabled: true });
    } else {
      modules.push({ type: m.type, enabled: true });
    }
  }

  const saved = await api('PUT', `/api/admin/home-themes/${id}/modules`, { modules });
  console.log(`   模块 ${saved.modules?.length ?? '?'} 个: ` + (saved.modules || []).map((x) => x.typeLabel || x.type).join(' → '));

  if (ACTIVATE) {
    const act = await api('POST', `/api/admin/home-themes/${id}/activate`);
    console.log(`✅ 已设为当前主题: ${act.name}`);
  } else {
    console.log('   （未设为当前主题，需要时加 --activate）');
  }
}

// ---------- 步骤 4：清理种子数据 ----------
async function stepCleanup() {
  console.log('\n═══ 清理 db/demo 种子数据 ═══');
  let off = 0, miss = 0;
  for (const title of DATA.cleanup.productTitles) {
    const p = await findProduct(title);
    if (!p) { miss++; continue; }
    if (p.status !== 'ON') continue;
    await api('POST', `/api/admin/products/${p.id}/offline`);
    console.log(`✅ 下线商品 ${title}`);
    off++;
  }
  for (const title of DATA.cleanup.lookTitles) {
    const l = await findLook(title);
    if (!l) { miss++; continue; }
    if (l.status !== 'ON') continue;
    await api('POST', `/api/admin/looks/${l.id}/offline`);
    console.log(`✅ 下线造型 ${title}`);
    off++;
  }
  for (const name of DATA.cleanup.themeNames) {
    const th = await findTheme(name);
    if (!th) { miss++; continue; }
    if (th.status !== 'ON') continue;
    await api('POST', `/api/admin/home-themes/${th.id}/offline`);
    console.log(`✅ 下线主题 ${name}`);
    off++;
  }
  console.log(`清理：下线 ${off} 项 / 不存在 ${miss} 项`);
}

// ---------- 主流程 ----------
async function main() {
  console.log(`目标: ${BASE}${isLocal ? ' (本地)' : '  ⚠️ 非本地环境'}`);
  if (!isLocal && !YES && !DRY) {
    console.error('拒绝对非本地环境执行。确认无误请加 --yes');
    process.exit(2);
  }
  if (!USER || !PASS) {
    console.error('缺少 XC_USER / XC_PASS 环境变量');
    process.exit(2);
  }
  console.log(`图片目录: ${IMG_DIR}`);

  const login = await api('POST', '/api/admin/auth/login', { username: USER, password: PASS });
  TOKEN = login.token;
  STORES = await api('GET', '/api/admin/stores');
  if (!Array.isArray(STORES)) STORES = STORES.list || [];
  console.log(`登录: ${login.staff.username} (${login.staff.role}) | 门店 ${STORES.length} 家`);

  if (cmd === 'products' || cmd === 'all') await stepProducts();
  if (cmd === 'collections') await stepCollections();
  if (cmd === 'all') await stepCollections();
  if (cmd === 'looks' || cmd === 'all') await stepLooks();
  if (cmd === 'theme' || cmd === 'all') await stepTheme();
  if (cmd === 'cleanup' || cmd === 'all') await stepCleanup();
  if (!['products', 'collections', 'looks', 'theme', 'cleanup', 'all'].includes(cmd)) {
    console.error(`未知子命令: ${cmd}`);
    process.exit(2);
  }
  console.log('\n完成。');
}

main().catch((e) => { console.error('失败:', e.message); process.exit(1); });
