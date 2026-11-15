import assert from 'node:assert/strict';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

export function validateStorefront(data) {
  const unique = (rows, field) => {
    assert(Array.isArray(rows), `缺少 ${field} 数据列表`);
    const values = rows.map((row) => row[field]);
    assert(values.every((value) => typeof value === 'string' && value.trim()), `${field} 不能为空`);
    assert.equal(new Set(values).size, values.length, `${field} 不允许重复`);
    return new Set(values);
  };
  const titles = unique(data.products, 'title');
  const collections = unique(data.collections, 'name');
  unique(data.looks, 'title');
  const productRef = (title) => assert(titles.has(title), `不存在的商品引用：${title}`);
  for (const p of data.products) {
    assert(typeof p.file === 'string' && p.file.trim(), `${p.title} 缺少图片文件名`);
    assert(Number.isFinite(p.price) && p.price > 0, `${p.title} 售价必须大于 0`);
    assert(Number.isFinite(p.origin) && p.origin >= 0, `${p.title} 划线价无效`);
    assert(Number.isInteger(p.sortWeight) && p.sortWeight >= 0, `${p.title} 排序无效`);
    for (const key of ['colors', 'sizes']) {
      assert(Array.isArray(p[key]) && p[key].length > 0 && p[key].every((v) => typeof v === 'string' && v.trim()), `${p.title} ${key} 无效`);
      assert.equal(new Set(p[key]).size, p[key].length, `${p.title} ${key} 重复`);
    }
  }
  const stocks = data.defaults?.stockByStore;
  assert(stocks && Object.keys(stocks).length > 0, '缺少门店库存');
  for (const qty of Object.values(stocks)) assert(Number.isInteger(qty) && qty >= 0, '库存必须为非负整数');
  for (const col of data.collections) {
    assert(Array.isArray(col.productTitles) && col.productTitles.length > 0, `${col.name} 缺少商品`);
    col.productTitles.forEach(productRef);
    assert.equal(new Set(col.productTitles).size, col.productTitles.length, `${col.name} 商品重复`);
  }
  for (const look of data.looks) {
    assert(Array.isArray(look.products) && look.products.length > 0, `${look.title} 缺少单品`);
    look.products.forEach(productRef);
    productRef(look.coverProduct);
  }
  assert(data.theme?.name, '缺少主题名称');
  productRef(data.theme.coverProduct);
  assert(Array.isArray(data.theme.modules) && data.theme.modules.length <= 8, '主题模块无效');
  for (const m of data.theme.modules) {
    assert(['BANNER', 'RECOMMEND', 'COLLECTION', 'LOOK', 'STORE'].includes(m.type), `未知模块：${m.type}`);
    if (m.type === 'COLLECTION') assert(collections.has(m.collectionName), `不存在的系列引用：${m.collectionName}`);
    if (m.type === 'STORE') assert(Object.hasOwn(stocks, m.storeName), `不存在的门店引用：${m.storeName}`);
    if (m.imageProduct) productRef(m.imageProduct);
  }
  for (const key of ['productTitles', 'lookTitles', 'themeNames']) assert(Array.isArray(data.cleanup?.[key]), `缺少清理列表：${key}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const data = JSON.parse(fs.readFileSync(new URL('./data/storefront.json', import.meta.url), 'utf8'));
  validateStorefront(data);
  console.log(`门店数据校验通过：${data.products.length} 商品 / ${data.collections.length} 系列 / ${data.looks.length} 造型`);
}
