# 门店数据录入脚本

把商品、造型、首页主题批量写入管理后台。适合首次铺货、换季上新、以及给新环境（本地/预发/生产）准备同一套数据。

## 为什么用脚本而不是后台点

一个商品要走 7 步：建商品 → 存 SKU → 传图 → 绑图 → 设封面 → 三店库存 → 上架。
16 款商品就是 110+ 次写操作，逐格点容易漏、且无法在环境间复现。脚本把这些做成可重复执行的步骤。

**按标题/名称匹配，不依赖数据库 id**，所以同一份数据在本地和生产都能跑，不会因为 id 不同而错位。

## 前置条件

- 管理后台可访问（本地 `localhost:8082`，生产 `https://admin.xiachen.xyz` 对应后端）
- 一个 ADMIN 或 MANAGER 账号
- 商品图在本地目录里（默认 `~/Downloads`）

## 用法

```bash
# 本地
XC_USER=admin XC_PASS=<密码> node scripts/seed-storefront.mjs all

# 生产：必须显式加 --yes，防止手滑
XC_BASE=https://admin.xiachen.xyz \
XC_USER=admin XC_PASS=<密码> \
node scripts/seed-storefront.mjs all --activate --yes
```

## 子命令

| 子命令 | 作用 |
| --- | --- |
| `products` | 商品 + SKU + 图片 + 三店库存 + 上架 |
| `collections` | 系列 + 系列内商品 + 上线 |
| `looks` | 造型 + 单品组合 + 发布 |
| `theme` | 首页主题 + 模块（加 `--activate` 设为当前主题） |
| `cleanup` | 下线 `db/demo` 种子数据（商品 / 造型 / 主题） |
| `all` | `products` → `collections` → `looks` → `theme` → `cleanup` |

## 参数

| 变量 / 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `XC_BASE` | `http://127.0.0.1:8082` | 后台 API 基址 |
| `XC_USER` | 必填 | 后台账号 |
| `XC_PASS` | 必填 | 后台密码 |
| `XC_IMG_DIR` | `~/Downloads` | 商品图所在目录 |
| `--dry` | 关 | 只打印将执行的写操作，不落库 |
| `--activate` | 关 | 主题保存后设为当前主题 |
| `--yes` | 关 | 允许对非本机地址执行 |
| `--only=N` | 全部 | 只处理前 N 个商品，用于试跑 |

## 数据源

所有内容在 [`data/storefront.json`](data/storefront.json)，改数据不用改代码：

- `products[]` —— 图片文件名、标题、副标题、分类、季节、价格、颜色、尺码、排序权重
- `looks[]` —— 造型标题、描述、封面取自哪个商品、包含哪些商品（**写商品标题**）
- `collections[]` —— 系列名、系列故事、包含哪些商品（**写商品标题**）、排序
- `theme` —— 主题名、文案、封面、模块列表
- `defaults.stockByStore` —— 按门店名给库存基数
- `cleanup` —— 属于 `db/demo` 的种子数据标题，上线前应下线

改完直接重跑，脚本是幂等的：已存在的更新、不存在的创建，不会重复建商品，也不会重复上传图片。商品、系列或造型有任一项失败时，脚本返回非零退出码，并停止后续步骤。执行前自动检查名称唯一性、引用关系、售价与库存；也可以单独运行 `node scripts/check-storefront.mjs`。

## 注意

- `all` 最后会执行 `cleanup`，按配置下线演示数据。补录系列时使用 `collections`，再执行 `theme --activate`，无需重跑商品与库存。
- 重跑 `products` / `all` 会按数据源重新写入价格与库存，不适合作为部署后的自动步骤。
- **价格与库存是业务数据**，`storefront.json` 里的值是合理占位，上线前请门店确认真实售价与备货。
- 首页主题的「系列(COLLECTION)」模块依赖已存在的系列；`all` 会先跑 `collections` 建好系列，所以按 `all` 或 `products → collections → looks → theme` 的顺序执行即可。单独跑 `theme` 时若系列不存在，会跳过该模块并打印 ⚠️。
- `collections` 步骤需要后台包含系列维护接口（`POST /api/admin/collections`）；旧版本后台会返回 `系统开小差了`，此时先部署后台再执行。
- 「店长推荐」不是手工选品，由商品 `sortWeight` 降序自动取前 4 件；想让某几件上榜，调它们的排序权重。
- 脚本不带任何凭据，密码只从环境变量读，不会写入仓库。
