# 夏晨衣舍数据字典

对应迁移：`V1__schema.sql`（结构）、`V2__seed_store.sql`（三店）、`V3`（预留未用）、`V4__seed_demo.sql`（演示商品，位于 `db/demo/`，仅 local profile 加载）、`V5__column_comments.sql`（字段注释）、`V6__drop_transfer_order.sql`（下线调货表）。生产 `spring.flyway.locations` 只有 `classpath:db/migration`，不会执行 `db/demo`。
数据库 `xiachen_yishe`，InnoDB，`utf8mb4` / `utf8mb4_0900_ai_ci`。

> 为什么 Navicat 里看不到字段说明：V1 只给少数列写了 `COMMENT`，表级 `COMMENT`（如「商品 SPU，全局共享」）在表列表中默认不展示。本文件是字段口径的权威说明；如后续希望数据库自己也带注释，可再加只含 `ALTER TABLE ... MODIFY COLUMN ... COMMENT` 的迁移，不改业务结构。

## 0. 全局约定

| 约定 | 说明 |
| --- | --- |
| 主键 | `id BIGINT UNSIGNED AUTO_INCREMENT` |
| 外键 | **不使用**数据库外键，关联由应用层保证，靠索引兜底 |
| 时间 | `DATETIME(3)`；`created_at` 默认当前时间；`updated_at` 默认当前时间并 `ON UPDATE CURRENT_TIMESTAMP(3)` |
| 回写时间 | MyBatis-Plus 实体的 `updatedAt` 均标注 `FieldStrategy.NEVER`，只让 MySQL 维护，避免把查询到的旧值写回 |
| 金额 | 统一 `INT` 存**分**（字段名带 `_cent`），禁止浮点；展示时除以 100 |
| 状态 | 字符串枚举，不用数字码：`ON/OFF`、`DRAFT/ON/OFF` 等；下线一律 `OFF`，不物理删除 |
| 布尔 | `TINYINT` 0/1：`on_sale`、`enabled`、`is_current` |
| 图片 | 存 URL + 原始宽高；宽高用于瀑布流/详情预占位，0 表示未知 |
| 多态 | `target_type`：`1` 商品（`product.id`）、`2` 造型（`look.id`） |
| 多 AppID | `app_user` 以 `(appid, openid)` 唯一；请求头 `X-App-Id` 决定 appid |
| 迁移 | Flyway 管理，禁止改已执行过的历史文件；新增变更用新版本号 |

### 关系速览

```
store 1─N staff / inventory
product 1─N product_image / sku
sku 1─N inventory / tryon_item / appointment_item
collection N─N product（collection_product）
look N─N product（look_item）
home_theme 1─N home_module
app_user 1─N user_favorite / user_like / user_footprint / tryon_item
           / appointment
appointment 1─N appointment_item
```

---

## 1. 门店与后台

### store — 门店

线上下单的三家门店（罗湖 / 福田 / 盐田），是库存与预约的归属维度。C 端只读 `ON` 门店；后台「门店」页可改名称、地址、电话、营业时间（`PUT /api/admin/stores/{id}`）与封面（`PUT /api/admin/stores/{id}/cover`）。ADMIN 可新增门店（`POST /api/admin/stores`）和上下线（`PUT /api/admin/stores/{id}/status`）；编码、排序仍只读。门店不物理删除，下线后 C 端列表/详情、首页门店卡、预约与试穿选店均不再出现。营业时间两端都填时预约按此校验，留空表示不限时段。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 门店 ID |
| name | VARCHAR(64) | NOT NULL | 全名，如「夏晨衣舍 · 罗湖店」 |
| code | VARCHAR(32) | NOT NULL，唯一 | 业务编码：`LUOHU` / `FUTIAN` / `YANTAN` |
| address | VARCHAR(255) | `''` | 门店地址 |
| phone | VARCHAR(32) | `''` | 门店电话 |
| open_time | TIME | NULL | 营业开始时间 |
| close_time | TIME | NULL | 营业结束时间 |
| lat / lng | DECIMAL(10,7) | NULL | 纬度 / 经度，地图与导航用 |
| cover_url | VARCHAR(512) | `''` | 门店封面图 |
| status | VARCHAR(16) | `'ON'` | `ON` 营业/展示，`OFF` 下线；ADMIN 在「门店」页切换 |
| sort_no | INT | 0 | 升序展示 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

### staff — 后台账号

店长/管理员登录后台（`xc-admin`）。密码只存 BCrypt 哈希，明文不落库；初始账号由 `AdminInitializer` 按环境变量创建。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 账号 ID，同时是 JWT `sub` |
| store_id | BIGINT UNSIGNED | NULL | 所属门店；`ADMIN` 可为空表示跨店 |
| username | VARCHAR(64) | NOT NULL，唯一 | 登录名 |
| password_hash | VARCHAR(100) | NOT NULL | BCrypt 哈希 |
| display_name | VARCHAR(64) | `''` | 后台显示名 |
| role | VARCHAR(16) | `'MANAGER'` | `ADMIN` 全量 / `MANAGER` 单店 |
| openid | VARCHAR(64) | NULL | 预留：店长微信绑定 |
| status | VARCHAR(16) | `'ON'` | `ON` 可登录，`OFF` 停用（登录返回 40103） |
| last_login_at | DATETIME(3) | NULL | 最近登录时间，登录成功时更新 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

### audit_log — 后台操作留痕

M1.5 起记录商品、SKU、图片、库存、首页主题/模块、门店等写操作；按留痕原则不物理删除。M4 新增动作：`HOME_THEME_CREATE/UPDATE/MODULES/ACTIVATE/OFFLINE`、`STORE_CREATE/UPDATE/STATUS/COVER_UPDATE`。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 日志 ID |
| staff_id | BIGINT UNSIGNED | NOT NULL | 操作人 `staff.id` |
| action | VARCHAR(64) | NOT NULL | 动作：`PRODUCT_CREATE/UPDATE/PUBLISH/OFFLINE`、`SKU_SAVE`、`IMAGE_BIND/COVER/UNBIND`、`UPLOAD`、`INVENTORY_UPDATE`、`HOME_CONFIG_UPDATE` |
| target_type | VARCHAR(32) | `''` | 对象类型：`PRODUCT`、`SKU`、`HOME_THEME` 等 |
| target_id | BIGINT UNSIGNED | NULL | 对象 ID |
| detail | JSON | NULL | 变更内容快照（请求体/差异） |
| ip | VARCHAR(45) | `''` | 客户端 IP，优先 `X-Forwarded-For` |
| created_at | DATETIME(3) | 当前时间 | 操作时间 |

索引：`(staff_id, created_at)` 查个人轨迹，`(target_type, target_id)` 查对象轨迹。

---

## 2. 商品与库存

### product — 商品 SPU（全局共享）

三店共用同一款商品，库存差异在 `inventory`。前台只展示 `status='ON'`。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 商品 ID，也是收藏/点赞 `target_id`（`target_type=1`） |
| title | VARCHAR(128) | NOT NULL | 商品名 |
| subtitle | VARCHAR(255) | `''` | 一句话卖点，列表卡与详情副标题 |
| category | VARCHAR(32) | `''` | 上装 / 下装 / 连衣裙 / 外套 / 配饰 |
| season | VARCHAR(16) | `''` | `SS` 春夏 / `AW` 秋冬 / `ALL` 四季 |
| material | VARCHAR(64) | `''` | 面料 |
| description | TEXT | NULL | 商品说明，详情页长文 |
| cover_url | VARCHAR(512) | `''` | 封面图；新商品为空，绑第一张图时自动设为主图 |
| cover_width | INT | 0 | 封面原始宽，瀑布流布局用 |
| cover_height | INT | 0 | 封面原始高 |
| status | VARCHAR(16) | `'DRAFT'` | `DRAFT` 草稿 / `ON` 在售 / `OFF` 已下线；下线不删 |
| sort_weight | INT | 0 | 店长推荐排序权重，越大越前 |
| like_count | INT UNSIGNED | 0 | 点赞数（冗余计数，点赞/取消时增减，不小于 0） |
| fav_count | INT UNSIGNED | 0 | 收藏数（同上） |
| online_at | DATETIME(3) | NULL | 首次上架时间；「上新」按此倒序 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

索引：`(status, category, sort_weight)`、`(status, online_at)`。
发布校验（后台）：至少 1 张图、至少 1 个 `ON` 且售价 > 0 的 SKU。

### product_image — 商品图片

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 图片 ID |
| product_id | BIGINT UNSIGNED | NOT NULL | 所属商品 |
| url | VARCHAR(512) | NOT NULL | 图片地址（本地 `/uploads/...` 或对象存储） |
| width | INT | 0 | 原始宽，上传时由 ImageIO 回写 |
| height | INT | 0 | 原始高 |
| sort_no | INT | 0 | 升序；`0` 为主图，详情页按此顺序轮播 |
| created_at | DATETIME(3) | 当前时间 | 上传/绑定时间 |

索引：`(product_id, sort_no)`。

### sku — SKU（颜色 × 尺码）

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | SKU ID；库存、清单、预约都引用它 |
| product_id | BIGINT UNSIGNED | NOT NULL | 所属商品 |
| color | VARCHAR(32) | `''` | 颜色，如「燕麦色」 |
| size | VARCHAR(16) | `''` | 尺码，如 `S/M/L` |
| price_cent | INT UNSIGNED | 0 | 售价（分） |
| origin_price_cent | INT UNSIGNED | 0 | 划线价（分），0 表示不展示 |
| sort_no | INT | 0 | 颜色×尺码展示顺序 |
| status | VARCHAR(16) | `'ON'` | `ON` 可售 / `OFF` 已剔除；后台整组保存时缺失项自动置 `OFF` |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

唯一键：`(product_id, color, size)`。

### inventory — 门店库存（手工维护）

店长按门店维护数量与是否在售；不接 POS，页面展示更新时间免责。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 库存行 ID |
| store_id | BIGINT UNSIGNED | NOT NULL | 门店 |
| sku_id | BIGINT UNSIGNED | NOT NULL | SKU |
| quantity | INT | 0 | 该店该 SKU 数量，≥0 |
| on_sale | TINYINT | 1 | 1 在本店上架可售 / 0 暂停售卖（数量不计入可售） |
| updated_at | DATETIME(3) | 自动更新 | 最近维护时间，详情页展示 |

唯一键：`(store_id, sku_id)`。
库存状态换算（`StockStatus`）：数量 0 → `OUT`；1–2 → `LOW`；≥3 → `IN_STOCK`。

---

## 3. 系列与造型

### collection — 系列

如「AW26 秋冬」。后台系列 CRUD 未做；首页 `COLLECTION` 模块读取，种子数据/手工维护。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 系列 ID，也是 `home_module.ref_id`（type=COLLECTION） |
| name | VARCHAR(64) | NOT NULL | 系列名 |
| cover_url | VARCHAR(512) | `''` | 系列封面 |
| story | TEXT | NULL | 系列故事/文案 |
| publish_at | DATETIME(3) | NULL | 计划发布时间 |
| status | VARCHAR(16) | `'DRAFT'` | `DRAFT/ON/OFF` |
| sort_no | INT | 0 | 升序 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

### collection_product — 系列内商品

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 关联 ID |
| collection_id | BIGINT UNSIGNED | NOT NULL | 系列 |
| product_id | BIGINT UNSIGNED | NOT NULL | 商品 |
| sort_no | INT | 0 | 系列内顺序 |

唯一键：`(collection_id, product_id)`；索引 `product_id`。

### look — 造型

一组搭配，对应小程序首页「造型灵感」。后台「造型维护」维护基本信息、封面（含宽高）、排序与上下线；首页造型卡使用 `cover_url`。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 造型 ID，也是收藏/点赞 `target_id`（`target_type=2`） |
| title | VARCHAR(128) | `''` | 造型名，如「焦糖棕 · 通勤一层层」 |
| description | TEXT | NULL | 搭配说明 |
| cover_url | VARCHAR(512) | `''` | 封面 |
| cover_width | INT | 0 | 封面原始宽 |
| cover_height | INT | 0 | 封面原始高 |
| status | VARCHAR(16) | `'DRAFT'` | `DRAFT/ON/OFF`，前台只读 `ON` |
| sort_no | INT | 0 | 升序 |
| like_count | INT UNSIGNED | 0 | 点赞数（冗余计数） |
| fav_count | INT UNSIGNED | 0 | 收藏数（冗余计数） |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

### look_item — 造型内商品

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 关联 ID |
| look_id | BIGINT UNSIGNED | NOT NULL | 造型 |
| product_id | BIGINT UNSIGNED | NOT NULL | 商品 |
| sort_no | INT | 0 | 造型内顺序 |

唯一键：`(look_id, product_id)`；索引 `product_id`。

---

## 4. 首页编排

### home_theme — 首页主题

一套主题包含若干模块；同一时刻只有一套 `is_current=1`，由应用保证（不做数据库唯一约束）。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 主题 ID |
| name | VARCHAR(64) | NOT NULL | 主题名（首页大图叠字） |
| cover_url | VARCHAR(512) | `''` | 主题封面；无 BANNER 图时兜底首屏大图 |
| copy_text | VARCHAR(255) | `''` | 主题文案 |
| start_at | DATETIME(3) | NULL | 生效开始（预留） |
| end_at | DATETIME(3) | NULL | 生效结束（预留） |
| status | VARCHAR(16) | `'DRAFT'` | `DRAFT/ON/OFF`；C 端要求 `ON` 且 `is_current=1` |
| is_current | TINYINT | 0 | 1 当前主题 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

### home_module — 首页模块

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 模块 ID |
| theme_id | BIGINT UNSIGNED | NOT NULL | 所属主题 |
| type | VARCHAR(16) | NOT NULL | `BANNER` 首屏大图 / `COLLECTION` 系列 / `RECOMMEND` 店长推荐 / `STORE` 门店卡 / `LOOK` 造型 |
| ref_id | BIGINT UNSIGNED | NULL | 引用：`COLLECTION`→`collection.id`、`STORE`→`store.id`；其余为空 |
| image_url | VARCHAR(512) | `''` | 模块图片；`BANNER` 即首屏大图 |
| sort_no | INT | 0 | 升序 |
| enabled | TINYINT | 1 | 1 展示 / 0 跳过 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

后台「首页主题」页维护主题与模块：草稿/启用/下线、时间窗、设为当前、模块增删排序。C 端选主题规则：`status=ON` 且当前时间在 `start_at`–`end_at` 内，优先 `is_current`，再按 `start_at`、`id` 倒序取一条。任意主题/模块/门店封面写入后失效 `xc:home:` 缓存。

---

## 5. 微信用户与行为

### app_user — 微信用户（多 AppID）

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 用户 ID，用户 JWT `sub` |
| appid | VARCHAR(32) | NOT NULL | 小程序 AppID，与 `openid` 联合唯一 |
| openid | VARCHAR(64) | NOT NULL | 微信用户在本 AppID 下的 openid；本地 mock 为 `mock_<appid>` |
| unionid | VARCHAR(64) | NULL | 同主体多应用打通用；jscode2session 返回时写入 |
| nickname | VARCHAR(64) | `'微信用户'` | 称呼；微信不再静默返回，由用户主动设置 |
| phone | VARCHAR(32) | NULL | 手机号，预留给 `/auth/phone`（依赖主体资质） |
| avatar_url | VARCHAR(512) | `''` | 头像地址；上传能力未接前为空，前端用昵称首字占位 |
| last_login_at | DATETIME(3) | NULL | 最近登录时间，每次 wx-login 更新 |
| created_at | DATETIME(3) | 当前时间 | 注册时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

唯一键：`(appid, openid)`；索引 `unionid`、`phone`。

### user_favorite — 收藏

多态指向商品或造型；与点赞语义不同，可同时存在。后台 `product.fav_count` / `look.fav_count` 同步增减。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 收藏行 ID |
| user_id | BIGINT UNSIGNED | NOT NULL | `app_user.id` |
| target_type | TINYINT | NOT NULL | 1 商品 / 2 造型 |
| target_id | BIGINT UNSIGNED | NOT NULL | `product.id` 或 `look.id` |
| created_at | DATETIME(3) | 当前时间 | 收藏时间，列表按此倒序 |

唯一键：`(user_id, target_type, target_id)`，重复收藏靠 `INSERT IGNORE` 幂等；索引 `(user_id, created_at)`。

### user_like — 点赞

结构与收藏一致，独立成表便于分别统计与展示。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 点赞行 ID |
| user_id | BIGINT UNSIGNED | NOT NULL | `app_user.id` |
| target_type | TINYINT | NOT NULL | 1 商品 / 2 造型 |
| target_id | BIGINT UNSIGNED | NOT NULL | 目标 ID |
| created_at | DATETIME(3) | 当前时间 | 点赞时间 |

唯一键：`(user_id, target_type, target_id)`；索引 `(user_id, created_at)`。

### user_footprint — 足迹

同一用户在同一门店看同一商品只保留一行，重复浏览刷新 `viewed_at`；只保留 30 天，读取时顺带清理过期行。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 足迹行 ID |
| user_id | BIGINT UNSIGNED | NOT NULL | 用户 |
| product_id | BIGINT UNSIGNED | NOT NULL | 商品 |
| store_id | BIGINT UNSIGNED | 0 | 浏览时的门店；0 表示未带门店 |
| viewed_at | DATETIME(3) | 当前时间，自动更新 | 最近浏览时间 |

唯一键：`(user_id, product_id, store_id)`；索引 `(user_id, viewed_at)`。

---

## 6. 试穿清单与预约

### tryon_item — 试穿清单

按 SKU 加入，同一用户同一 SKU 同一门店唯一；清单页按门店分组。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 清单行 ID |
| user_id | BIGINT UNSIGNED | NOT NULL | 用户 |
| sku_id | BIGINT UNSIGNED | NOT NULL | 具体颜色×尺码 |
| store_id | BIGINT UNSIGNED | 0 | 计划试穿门店 |
| created_at | DATETIME(3) | 当前时间 | 加入时间 |
| updated_at | DATETIME(3) | 自动更新 | 更新时间 |

唯一键：`(user_id, sku_id, store_id)`；索引 `(user_id, store_id)`。

### appointment — 预约到店

**不扣库存、不限人数**。`client_request_id` 用于客户端幂等，防止重复提交。

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 预约 ID |
| user_id | BIGINT UNSIGNED | NOT NULL | 用户 |
| store_id | BIGINT UNSIGNED | NOT NULL | 到店门店 |
| visit_date | DATE | NOT NULL | 到店日期 |
| visit_time | TIME | NULL | 到店时间，30 分钟一档，可为空 |
| item_count | INT | 0 | 预约商品件数（快照，方便看板） |
| phone | VARCHAR(32) | NOT NULL | 联系手机号 |
| contact_name | VARCHAR(32) | `''` | 称呼 |
| remark | VARCHAR(255) | `''` | 备注 |
| status | VARCHAR(16) | `'PENDING'` | `PENDING` 待确认 / `CONFIRMED` 已确认 / `DONE` 已到店 / `CANCELED` 已取消 |
| client_request_id | VARCHAR(64) | NULL | 客户端幂等键 |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |
| updated_at | DATETIME(3) | 自动更新 | 状态更新时间 |

唯一键：`(user_id, client_request_id)`；索引 `(store_id, visit_date, status)`、`(user_id, created_at)`。

### appointment_item — 预约包含的商品

| 字段 | 类型 | 空/默认 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | PK | 行 ID |
| appointment_id | BIGINT UNSIGNED | NOT NULL | 预约 |
| sku_id | BIGINT UNSIGNED | NOT NULL | SKU |
| store_id | BIGINT UNSIGNED | NOT NULL | 门店（快照，便于看板按店查） |
| created_at | DATETIME(3) | 当前时间 | 创建时间 |

索引：`appointment_id`。注意：没有唯一键，同一预约理论上可重复记录同一 SKU，应用层去重。

---

## 7. Flyway 元数据

### flyway_schema_history

Flyway 自动维护的迁移记录，**不要手工修改或删除**。

| 字段 | 说明 |
| --- | --- |
| installed_rank | 执行顺序 |
| version | 版本号（1 / 2 / 4 / 5 / 6；3 预留未用） |
| description | 迁移描述 |
| type | `SQL` 等 |
| script | 文件名 |
| checksum | 校验和；改历史文件会导致启动校验失败 |
| installed_by / installed_on | 执行者 / 执行时间 |
| execution_time | 耗时（ms） |
| success | 是否成功 |

当前版本：`V1` 结构、`V2` 三店种子、`V4` 演示商品（10 款商品 / 60 SKU / 180 条库存 / 造型数据，仅 local 加载）、`V5` 字段注释（21 张表 181 个字段补 COMMENT）、`V6` 删除 `transfer_order`。新增结构变更请用 `V7__...sql` 起，且放在 `db/migration`；只有演示/体验数据的迁移才放 `db/demo`。

---

## 8. 与代码的对应

| 表 | 主要写入路径 | 主要读取路径 |
| --- | --- | --- |
| store / inventory | 后台：库存矩阵、门店新增 `POST /api/admin/stores`、上下线 `PUT /api/admin/stores/{id}/status`、门店信息与封面 `PUT /api/admin/stores/{id}`、`PUT /api/admin/stores/{id}/cover` | `/api/v1/stores`、商品详情三店库存、首页门店卡、预约明细 |
| staff / audit_log | 后台登录、后台写操作 | 后台登录、审计查询（暂无页面） |
| product / product_image / sku | 后台商品编辑、图片上传、SKU 整组保存 | 首页、上新、详情 |
| collection(_product) / look(_item) | 造型后台 `/api/admin/looks`（基本信息、单品、发布/下线）；系列暂为种子/手工 | 首页系列与造型模块、`/api/v1/looks` |
| home_theme / home_module | 后台「首页主题」`/api/admin/home-themes`（列表/详情/新建/编辑、模块全量保存、设为当前、下线） | `/api/v1/home` |
| app_user | `/api/v1/auth/wx-login`、`/auth/profile` | `/auth/me`、收藏点赞鉴权 |
| user_favorite / user_like | `/api/v1/favorites`、`/likes` | 同上 + 商品详情登录态 |
| user_footprint | 商品详情（登录态） | `GET /api/v1/footprints?limit`、`DELETE /api/v1/footprints` |
| tryon_item | `/api/v1/tryon-items` | `/tryon-items`、商品详情 `inCart` |
| appointment / appointment_item | `/api/v1/appointments`、`PATCH /appointments/{id}`；后台看板 `PATCH /api/admin/appointments/{id}` | `/appointments?page&pageSize`、`/appointments/{id}`；后台 `GET /api/admin/appointments?date&storeId&status` |

> `transfer_order`（跨店调货）已下线，V6 迁移删除该表；顾客侧不再提供调货入口。
