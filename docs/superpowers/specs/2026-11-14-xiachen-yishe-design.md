# 夏晨衣舍 · 开发设计文档

- 版本：v0.5
- 日期：2026-11-14
- 状态：待评审
- 关联需求：[夏晨衣舍女装 · 微信小程序 需求明细 v0.6](https://app.notion.com/p/418a36e1ae8a81f3b57df85ba430b267)
- 原型：Ardot 高保真原型（14 屏，我的预约原型待补）
- 同步说明：本文件与 Notion 开发设计文档 v0.3 内容一致，Notion 为评审入口

---

## 0. 合并说明

本版由两份 v0.1 合并而成：

- **来源 A**：Notion 上的同名文档（较早）
- **来源 B**：本仓库的 `docs/superpowers/specs/2026-11-14-xiachen-yishe-design.md`（较新，含 `inventory.updated_at`、`appointment.item_count`、`GET /appointments`、幂等键、健康检查等增量）

合并原则：取并集；冲突以 2026-11-14 评审决策为准。本版同时纳入四项已确认决策（造型实体、库存口径、清单跨店、启动节奏）。

### 0.1 本版变更清单

| 编号 | 变更 | 类型 |
| --- | --- | --- |
| C1 | 新增 `look` / `look_item`，收藏与点赞改为多态 | 模型 |
| C2 | `product_image` 增 `width/height`；`product`、`look` 增点赞/收藏计数 | 模型 |
| C3 | `tryon_item` 增唯一键；`appointment` 增 `client_request_id` | 模型 |
| C4 | `user_footprint` 改为覆盖更新 | 模型 |
| C5 | 补 `/looks`、`/looks/{id}`、`PATCH /appointments/{id}` | 接口 |
| C6 | 明确试穿清单按门店分组 | 流程 |
| C7 | 里程碑改为纵切，后台商品/库存提前到 M1.5 | 计划 |
| C8 | 新增微信能力约束与库存时效风险 | 风险 |
| C9 | 仓库目录 `backend/` → `xc/`，后台前端 Vue 3 → React 18；新增 §10.1 发布与 CI/CD | 工程 |

---

## 1. 背景与目标

夏晨衣舍（原夏晨晨）是经营 12 年的深圳女装品牌，3 家门店（罗湖 / 福田 / 盐田）。本系统建设微信小程序与 H5 管理后台，**不做线上交易**，核心链路是「浏览 → 收藏/点赞 → 试穿清单 → 预约到店」。

### 1.1 目标

1. 顾客免登录浏览三店在售商品，登录后可收藏、点赞、加清单、预约。
2. 店长在手机浏览器上维护商品、图片、尺码、库存、门店信息与预约。
3. 首页、上新、商品、库存全部可配置，不需要为内容变更发版。
4. 系统在单台 3.6 GB 内存的云服务器上与 cost、resume 共存。

### 1.2 非目标

- 在线支付、订单、物流、退款、评价、销量
- 会员积分、优惠券、分销
- 跨店调拨的物流追踪（只记录到店状态）
- 多语言、多品牌

### 1.3 已确认决策（2026-11-14）

| 议题 | 结论 | 对设计的影响 |
| --- | --- | --- |
| 造型 / 穿搭 | 新建 `look` + `look_item` 实体 | 收藏、点赞改多态；首页造型浮层有数据来源 |
| 库存口径 | 店长手工维护 + 页面免责 | 不做 POS 对接；详情页展示库存更新时间与电话确认提示 |
| 清单跨店 | 按门店分组，预约按门店分别发起 | `tryon_item.store_id` 保留；清单接口按门店聚合返回 |
| 启动节奏 | 前台纵切，后台紧跟（M1.5） | 里程碑重排，先打通 3 个真实商品的完整链路 |
| 工程结构 | `xc/`（后端）+ `miniprogram/` + `web/`（React） | 三端独立构建，按路径触发 CI/CD |

---

## 2. 总体架构

```
微信小程序（C 端）        浏览器（H5 管理后台）
        │                        │
   xc-api.xiachen.xyz      admin.xiachen.xyz
        │                        │
        └──────── Caddy 网关 ────┘
                     │
        ┌────────────┴─────────────┐
        │                          │
   xc-api :8081              xc-admin :8082
   （C 端接口）               （后台接口 + 静态页）
        │                          │
        ├──────── MySQL 8 ─────────┤   xiachen_yishe 库（复用实例）
        ├──────── Redis ───────────┤   db2 / db3（复用实例）
        └──────── COS ─────────────┘   img.xiachen.xyz（图片）
```

### 2.1 服务划分

| 服务 | 端口 | 职责 | JVM |
| --- | --- | --- | --- |
| `xc-api` | 8081 | 首页、商品、造型、收藏、点赞、足迹、试穿清单、预约、微信登录 | `-Xmx384m` |
| `xc-admin` | 8082 | 商品/造型与图片维护、库存、门店、系列与首页主题、预约看板、账号权限 | `-Xmx256m` |

两个服务**共用同一个 MySQL 库与 Redis 实例**。这是有意的折中：以受众为边界（顾客 / 店长），而不是按业务域拆；当前规模下拆更多只会增加运维成本，不产生收益。

### 2.2 边界约定

- `xc-api` 只读业务数据，写操作限于用户行为（收藏、点赞、足迹、清单、预约）。
- `xc-admin` 是唯一的商品、造型与库存写入口。
- 两服务不通过 HTTP 互相调用；共享的只有数据库表与缓存 key 约定。
- 后台接口统一挂在 `/api/admin/**`，便于将来独立成域。

---

## 3. 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 语言 | Java 21 (LTS) | 虚拟线程用于 I/O 密集接口 |
| 框架 | Spring Boot 3.4 | Web、Validation、Actuator |
| ORM | MyBatis-Plus | SQL 可控，库存与列表查询直观 |
| 数据库 | MySQL 8+ | 复用 `cost-cloud-mysql`，新库 `xiachen_yishe` |
| 缓存 | Redis 6+ | 复用实例，db2 / db3 + 前缀 + ACL |
| 迁移 | Flyway | 显式版本，禁止手工改表 |
| 鉴权 | JWT（C 端 + 后台）、RBAC（后台） | 微信登录换取 token |
| 网关 | Caddy | 复用 `xiachen-public-gateway` |
| 对象存储 | 腾讯云 COS | 后端 SDK 上传，自定义域名 `img.xiachen.xyz`；未配置凭证启动即失败 |
| 后台前端 | React 18 + Vite + TypeScript | H5 管理后台，手机浏览器可维护 |
| 构建 | Maven 多模块 + Docker | CI 产出 `linux/amd64` 镜像，服务器只运行 |

---

## 4. 工程结构

```
xiachen-yishe/
├── xc/                          Java 后端（Maven 多模块）
│   ├── pom.xml                  聚合
│   ├── Dockerfile               运行镜像
│   ├── xc-common/               通用：异常、响应体、缓存封装、鉴权、审计
│   ├── xc-domain/               实体、Mapper、领域服务（两服务共用）
│   ├── xc-api/                  C 端应用（port 8081）
│   └── xc-admin/                后台应用（port 8082）
├── miniprogram/                 微信小程序（原生 JS/WXML）
├── web/                         H5 管理后台前端（React 18 + Vite + TypeScript）
├── deploy/
│   ├── docker-compose.yml
│   ├── caddy/                   站点配置片段
│   └── sql/                     仅放只读说明，实际迁移走 Flyway
├── .github/workflows/           CI/CD（按路径触发）
└── docs/
    └── superpowers/
        ├── specs/               设计与规格
        └── plans/               实施计划
```

约定：`xc-domain` 被两个应用依赖，**禁止**在 `xc-domain` 里引入 Web 依赖。

### 4.1 多小程序 AppID 支持

开发期临时复用 cost 的 AppID（`wxfef7698185824d972`）做体验版演示，正式上线再换成新的夏晨衣舍 AppID。为避免上线时改代码，登录链路从一开始就按「多 AppID」设计：

```yaml
wechat:
  apps:
    - appid: wxfef7698185824d972
      secret: ${WX_SECRET_COST}
      label: cost
    - appid: wx????????????????
      secret: ${WX_SECRET_XC}
      label: xiachen
```

- 请求头携带 `X-App-Id`，或由网关按 Host 映射到具体 AppID
- `app_user` 表记录 `appid`，openid 与 appid 联合唯一
- 未注册的 AppID 先用占位符，注册后只改配置，不改代码

> **前置核实**：手机号快速验证组件要求小程序为**非个人主体且已认证**。若 cost 的 AppID 是个人主体，`/auth/phone` 与「手机号一键填充」不可用，需改为手动输入并短信验证（或砍掉）。开工前必须先确认。

---

## 5. 数据模型

库名 `xiachen_yishe`，字符集 `utf8mb4`，引擎 InnoDB。金额统一用 `int` 存**分**，禁止浮点。

21 张业务表由 `V1__schema.sql` 建立，无外键约束，关系与一致性由应用层保证。逐表逐字段的口径见 [数据字典](2026-11-14-data-dictionary.md)。

### 5.1 门店与人员

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `store` | id, name, code, address, phone, open_time, close_time, lat, lng, cover_url, status, sort_no | 初始三家门店；`code` 唯一；后台可新增与上下线，不物理删除 |
| `staff` | id, store_id, username, password_hash, display_name, role(ADMIN/MANAGER), openid, status, last_login_at | 后台账号；ADMIN 可跨店 |
| `audit_log` | id, staff_id, action, target_type, target_id, detail(JSON), ip, created_at | 后台操作留痕 |

### 5.2 商品

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `product` | id, title, subtitle, category, season, material, description, cover_url, cover_width, cover_height, status(DRAFT/ON/OFF), sort_weight, like_count, fav_count, online_at | SPU，全局共享；计数为冗余字段；下线用 `OFF` 不删行 |
| `product_image` | id, product_id, url, width, height, sort_no | 多图，主图取 sort_no 最小 |
| `sku` | id, product_id, color, size, price_cent, origin_price_cent, sort_no, status | 颜色 × 尺码；唯一键 (product_id, color, size) |
| `inventory` | id, store_id, sku_id, quantity, on_sale, updated_at | **按门店**；唯一键 (store_id, sku_id) |
| `collection` | id, name, cover_url, story, publish_at, status, sort_no | 系列，如 AW26 秋冬 |
| `collection_product` | collection_id, product_id, sort_no | 系列内商品与排序 |

> `product_image.width/height` 是**必填**。双列瀑布流必须先知道宽高比才能计算列高，否则首屏会跳动或退化成等高网格。`product.cover_width/cover_height` 用于列表页直接取封面尺寸。后台录入与 COS 直传完成后都要回写这几个值。

### 5.3 造型

造型 = 一套搭配（如「本造型 · 3 件」），与系列（图册，16 款）语义不同，独立建模。

```sql
CREATE TABLE look (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title         VARCHAR(128) NOT NULL DEFAULT '',
  description   TEXT NULL,
  cover_url     VARCHAR(512) NOT NULL DEFAULT '',
  cover_width   INT NOT NULL DEFAULT 0,
  cover_height  INT NOT NULL DEFAULT 0,
  status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT',   -- DRAFT/ON/OFF
  sort_no       INT NOT NULL DEFAULT 0,
  like_count    INT UNSIGNED NOT NULL DEFAULT 0,
  fav_count     INT UNSIGNED NOT NULL DEFAULT 0,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_look_status (status, sort_no)
);

CREATE TABLE look_item (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  look_id    BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  sort_no    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_look_item (look_id, product_id)
);
```

`look_item` 关联 **product** 而非 sku：造型用于搭配展示，具体尺码仍由商品详情页承担。

### 5.4 首页

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `home_theme` | id, name, cover_url, copy_text, start_at, end_at, status, is_current | 多套主题 |
| `home_module` | id, theme_id, type(BANNER/COLLECTION/LOOK/RECOMMEND/STORE), ref_id, image_url, sort_no, enabled | 模块编排 |

`is_current` 标记当前生效主题；定时切换由查询时按 `start_at/end_at` 判定，不依赖定时任务。唯一性由应用层保证，不加数据库约束。

主题选择（`CatalogService.home`）：取 `status=ON` 且当前时间落在 `[start_at, end_at]`（空值视为不限）的主题，按 `is_current`、`start_at`、`id` 倒序取第一条；运营手动「设为当前」的优先级最高。

模块约束（后台保存时校验）：`BANNER / RECOMMEND / STORE / LOOK` 各最多一个；`COLLECTION` 最多 3 个且同一系列只能出现一次；系列、门店引用必须存在。启用主题前必须已启用 BANNER 且首屏图或主题封面非空。任意主题/模块/门店写入后失效 `xc:home:` 缓存。

### 5.5 用户与行为

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `app_user` | id, appid, openid, unionid, nickname, phone, avatar_url, last_login_at | 唯一键 (appid, openid) |
| `user_favorite` | id, user_id, target_type, target_id, created_at | 唯一键 (user_id, target_type, target_id) |
| `user_like` | id, user_id, target_type, target_id, created_at | 与收藏分表，语义不同 |
| `user_footprint` | id, user_id, product_id, store_id, viewed_at | 唯一键 (user_id, product_id, store_id)，重复浏览只更新 viewed_at |
| `tryon_item` | id, user_id, sku_id, store_id, created_at, updated_at | 唯一键 (user_id, sku_id, store_id) |
| `appointment` | id, user_id, store_id, visit_date, visit_time, item_count, phone, contact_name, remark, status(PENDING/CONFIRMED/DONE/CANCELED), client_request_id | 预约到店 |
| `appointment_item` | appointment_id, sku_id, store_id | 预约包含的商品 |

**多态约定**：`target_type` 1=商品，2=造型；`target_id` 指向 `product.id` 或 `look.id`。收藏与点赞可对同一条内容同时存在。

幂等：`appointment` 的 `client_request_id` 由前端生成，后端按 (user_id, client_request_id) 唯一键兜底重复提交。

足迹只保留最近一次浏览记录与时间，不做浏览次数累计；30 天清理。

### 5.6 索引

- `user_favorite(user_id, target_type, target_id)` 唯一 + `(user_id, created_at)`
- `user_like(user_id, target_type, target_id)` 唯一 + `(user_id, created_at)`
- `user_footprint(user_id, product_id, store_id)` 唯一 + `(user_id, viewed_at)`
- `inventory(store_id, sku_id)` 唯一
- `product(status, category, sort_weight)`、`product(status, online_at)`
- `look(status, sort_no)`、`look_item(look_id, product_id)` 唯一
- `appointment(store_id, visit_date, status)`、`appointment(user_id, created_at)`

---

## 6. 接口设计

统一返回 `{code, message, data}`，`code=0` 表示成功。分页参数 `page`、`size`（默认 20，上限 50）。

### 6.1 C 端 `/api/v1`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/wx-login` | code 换 openid，返回 JWT |
| POST | `/auth/phone` | 手机号解密绑定（依赖主体资质，见 §4.1） |
| GET | `/home` | 当前主题 + 模块 + 门店胶囊数据（可带 storeId） |
| GET | `/products` | 列表：category、sort、onlyStock、color、size、material、storeId、keyword；返回 likeCount / favCount / stockStatus |
| GET | `/products/{id}` | 详情 + 三店库存 + 用户状态（收藏/点赞/是否在清单） |
| GET | `/looks`、`/looks/{id}` | 造型列表与详情（含 3 件单品） |
| GET | `/stores`、`/stores/{id}` | 门店列表与详情 |
| GET/POST/DELETE | `/favorites` | 收藏；支持 targetType=1/2 过滤 |
| GET/POST/DELETE | `/likes` | 点赞；支持 targetType=1/2 过滤 |
| GET/DELETE | `/footprints` | 足迹（默认 50 条，30 天，可整体清空） |
| GET/POST/PATCH/DELETE | `/tryon-items` | 试穿清单；GET 按门店分组返回 |
| POST | `/appointments` | 预约到店（带 client_request_id） |
| GET | `/appointments`、`/appointments/{id}` | 我的预约 / 详情 |
| PATCH | `/appointments/{id}` | 用户取消预约 |

### 6.2 后台 `/api/admin/v1`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 账号密码登录 |
| CRUD | `/products` | 商品与 SKU、图片（含宽高） |
| CRUD | `/looks` | 造型与造型单品 |
| PATCH | `/inventory` | 单条或批量改库存 / 在售 |
| GET | `/stores` | 营业中门店下拉选项 |
| GET | `/stores/covers` | 门店完整信息（名称、地址、电话、营业时间、封面、状态） |
| POST | `/stores` | 新增门店（仅 ADMIN；编码唯一，默认 ON，排序排最后） |
| PUT | `/stores/{id}` | 更新门店名、地址、电话、营业时间（MANAGER 限本店） |
| PUT | `/stores/{id}/status` | 上线 / 下线门店（仅 ADMIN；不物理删除） |
| PUT | `/stores/{id}/cover` | 更新门店封面（首页门店卡使用，MANAGER 限本店） |
| GET | `/collections` | 系列下拉选项（不含 OFF） |
| CRUD | `/home-themes` | 主题列表/详情/新建/编辑 |
| PUT | `/home-themes/{id}/modules` | 全量保存模块（顺序即展示顺序） |
| POST | `/home-themes/{id}/activate` | 设为当前主题（要求 ON 且已有首屏图） |
| POST | `/home-themes/{id}/offline` | 下线并取消当前标记 |
| GET / GET `{id}` / PATCH | `/appointments` | 预约看板：按日期与门店筛选、明细、确认 / 到店 / 取消 |
| CRUD / 发布 / 下线 | `/looks` | 造型维护：基本信息、封面、排序、单品与上下线 |
| POST | `/uploads` | 上传图片（后端写入 COS，返回 url / 宽高） |
| CRUD | `/staff` | 账号与权限（仅 ADMIN） |

权限规则：`MANAGER` 只能读改自己 `store_id` 的数据；`ADMIN` 全量。

---

## 7. 关键流程

### 7.1 微信登录

1. 小程序 `wx.login` 取 code → `POST /auth/wx-login`
2. 后端调 `jscode2session` 换 openid
3. 查 `app_user`：无则插入（昵称「微信用户」）
4. 返回 JWT（7 天）+ 用户信息
5. 收藏等接口要求 `Authorization: Bearer <token>`；浏览类接口允许匿名

> 微信自 2022-10 起不再返回真实昵称与头像。`chooseAvatar` 与 `input type="nickname"` 只能由用户主动确认，**不能静默读取**。

### 7.2 预约到店

1. 清单按门店分组展示，用户勾选某门店的商品 → 打开预约浮层
2. 选门店、日期、时间（30 分钟一档，可不填）、手机号、称呼、备注
3. `POST /appointments`：写 `appointment` + `appointment_item`，**不扣库存、不限人数**
4. 后台看板确认后，小程序「我的预约」显示状态，用户可自行取消

重复提交由 `client_request_id` 幂等（后端唯一键兜底）。

### 7.3 试穿清单跨店

清单按门店分组返回，每组独立显示件数与「预约到店」入口。切换门店只改变默认分组，不改变已有条目归属。预约只提交当前所选门店的商品，其他门店条目保留在清单中。

---

## 8. 缓存设计

| 数据 | Key | TTL | 失效 |
| --- | --- | --- | --- |
| 首页主题 | `xc:home:{storeId}` | 10 min | 后台发布主题时删 |
| 商品详情 | `xc:product:{id}` | 5 min | 编辑商品时删 |
| 造型详情 | `xc:look:{id}` | 5 min | 编辑造型时删 |
| 列表结果 | `xc:list:{storeId}:{sha1(params)}` | 60 s | 商品/库存变更按前缀删 |
| 库存 | `xc:stock:{storeId}:{skuId}` | 30 s | 写操作即时更新 |
| 收藏/点赞数 | `xc:count:fav:{type}:{id}` / `xc:count:like:{type}:{id}` | 10 min | 计数递增 + 异步校正 |
| 后台会话 | `xca:sess:{token}` | 8 h | 登出删除 |

防护：

- 穿透：不存在的 id 缓存空值 60 s
- 击穿：热点 key 互斥锁（Redis `SET NX PX`）
- 雪崩：TTL 随机抖动 ±20%
- 约定：单 key ≤ 50 KB，禁止 `KEYS`，集合用 `SSCAN`

Redis 实例级设置：`maxmemory 512mb`、`maxmemory-policy volatile-lru`、缓存实例关闭 RDB/AOF。

---

## 9. 图片与上传

- 上传入口 `POST /api/admin/uploads`（multipart），后端校验格式与尺寸后写入腾讯云 COS；浏览器无需直连存储
- 桶 `xiachenchen-images-1513506595`，地域 `ap-guangzhou`，对象键 `{COS_KEY_PREFIX}/yyyyMM/{uuid}.jpg`
- 同一桶按环境分目录：本地 `dev/`，生产 `prod/`（`COS_KEY_PREFIX` 必须显式配置，缺失启动即失败）；已有的演示/测试图片仍在历史 `uploads/` 目录下
- 对外地址 = `COS_PUBLIC_BASE` + 键，生产为 `https://img.xiachen.xyz`
- 上传时对象 ACL 设为公有读；存储桶保持私有写
- 规格：原图 ≤ 2000px 宽；列表图 380px；详情图 760px，WebP，质量 75
- **宽高必填**：上传响应直接返回 `width/height`，瀑布流据此计算列高
- 密钥只走环境变量 `COS_SECRET_ID` / `COS_SECRET_KEY`，禁止入库或提交仓库；缺失时启动即失败
- 小程序需把 `img.xiachen.xyz` 加入 downloadFile 合法域名

---

## 10. 部署

```
xc-api    :8081  mem_limit 768m  -Xmx384m ZGC
xc-admin  :8082  mem_limit 512m  -Xmx256m ZGC
（复用）cost-cache-redis、cost-cloud-mysql
```

Caddyfile 片段：

```caddy
xc-api.xiachen.xyz {
    reverse_proxy 127.0.0.1:8081
}

admin.xiachen.xyz {
    handle /api/* {
        reverse_proxy 127.0.0.1:8082
    }
    handle {
        root * /srv/web
        try_files {path} /index.html
        file_server
    }
}
```

健康检查：`/actuator/health`（仅内网可访问，Caddy 不对外暴露）。

构建：CI 产出 `linux/amd64` 镜像，服务器 `docker compose pull && up -d`，**不在服务器上编译**。

### 10.1 发布与 CI/CD

仓库为 monorepo，四个可独立发布的产物：

| 产物 | 目录 | 发布方式 |
| --- | --- | --- |
| xc-api | `xc/` | 构建镜像 → 更新 xc-api 容器 |
| xc-admin | `xc/` | 构建镜像 → 更新 xc-admin 容器 |
| web | `web/` | vite build → 同步 dist 到 /srv/web |
| 小程序 | `miniprogram/` | 微信开发者工具 / CI 上传体验版，走微信审核 |

按路径触发（GitHub Actions `on.push.paths`）：

| 工作流 | 触发路径 |
| --- | --- |
| `xc-api.yml` | `xc/xc-api/**`、`xc/xc-common/**`、`xc/xc-domain/**`、`xc/pom.xml`、`xc/Dockerfile` |
| `xc-admin.yml` | `xc/xc-admin/**`、`xc/xc-common/**`、`xc/xc-domain/**`、`xc/pom.xml`、`xc/Dockerfile` |
| `web.yml` | `web/**` |
| `miniprogram.yml` | `miniprogram/**` |

后台两个服务共用 `_backend.yml`（workflow_call），差异只有 module / image / service / tag 变量四项。

部署开关与凭据：仓库变量 `DEPLOY_ENABLED=true` 才执行部署 job；Secrets 需要 `DEPLOY_SSH_KEY`、`DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_PATH`、`DEPLOY_WEB_ROOT`（默认 `/srv/web`）。镜像推送到 ghcr.io，标签为完整 commit sha + latest；服务器 `.env` 用 `XC_IMAGE_REPO` 指定仓库前缀。

关键约束：

- `xc-common` / `xc-domain` 被两个服务共用，改动这两个目录必须**同时重建 xc-api 与 xc-admin**，否则共享库与运行实例会版本漂移。
- 镜像标签使用完整 commit sha，不只 latest，便于回滚。
- 单容器更新用 `docker compose up -d --no-deps xc-api`，不影响另一个服务。
- 前端是静态产物，不占容器；发布即替换 `/srv/web`。

---

## 11. 可观测性与运维

- 日志：JSON 行格式，输出到 stdout，由 Docker 收集；保留 7 天
- 慢 SQL：MySQL `slow_query_log`，阈值 200 ms
- 指标：Actuator + Micrometer，暴露 JVM、连接池、缓存命中率
- 健康检查：`deploy/scripts/healthcheck.sh` 每 5 分钟由 cron 调用，校验 xc-api / xc-admin 的 `/actuator/health` 与 `/api/v1/home`；失败追加 `/var/log/xc-healthcheck.log`，配置 `ALERT_WEBHOOK_URL` 时推企业微信机器人
- 容器日志：json-file 驱动，单文件 10 MB、保留 3 个
- 备份：每日 `mysqldump --single-transaction` 压缩后上传 COS，保留 30 天

---

## 12. 数据迁移与初始化

Flyway 版本规划：

- `V1__schema.sql`：建表与索引（21 张表，含 `look` / `look_item`）— **已完成**
- `V2__seed_store.sql`：三家门店
- `V3__seed_admin.sql`：初始管理员（密码从环境变量注入，禁止明文入库）
- `V4__seed_demo.sql`：3 个真实款 + 三店差异化库存（仅开发/体验环境）；文件放在 `db/demo/`，只在 local profile 的 `spring.flyway.locations` 中加载，生产只用 `classpath:db/migration`
- `V5__column_comments.sql`：为 21 张表的 181 个字段补 `COMMENT`，口径与[数据字典](2026-11-14-data-dictionary.md)一致 — **已完成**
- `V6__drop_transfer_order.sql`：下线跨店调货，删除 `transfer_order` — **已完成**
- `V7__seed_images_to_cos.sql`：把 V4 演示数据的本地 `/seed/` 图片地址替换为 `img.xiachen.xyz`（同样只在 `db/demo/` 加载） — **已完成**
- `V8__seed_images_to_dev.sql`：把演示图片从历史 `uploads/` 目录迁到 `dev/` 目录（配合 `COS_KEY_PREFIX` 环境隔离） — **已完成**

---

## 13. 测试策略

| 层 | 手段 |
| --- | --- |
| 单元测试 | JUnit 5 + AssertJ，覆盖库存计算、缓存 key、权限判定 |
| 集成测试 | Testcontainers 起 MySQL 8 + Redis，跑 Flyway 后验证 Mapper 与接口 |
| 接口测试 | MockMvc，覆盖鉴权、分页、错误码 |
| 前端 | 小程序用现有 validate 脚本；后台用 Vitest |
| 手工 | 微信开发者工具 + 真机，记录验收状态 |

---

## 14. 上线依赖（非代码）

| 事项 | 状态 | 说明 |
| --- | --- | --- |
| 临时 AppID | ✅ 复用 cost | 仅做体验版演示，**不提审、不发布** |
| cost AppID 主体类型核实 | 待办 | 个人主体则手机号能力不可用，需改方案 |
| 微信小程序注册 | 待办 | 企业主体，营业执照，名称「夏晨衣舍」 |
| 新 AppID 域名重配 | 待办 | 新 AppID 需重新填写 request/uploadFile/downloadFile |
| 小程序备案 | 待办 | 平台提交管局，约 20 个工作日（关键路径） |
| 微信认证 | 待办 | 300 元/年 |
| 服务类目 | 待办 | 服饰内衣 / 商家自营 |
| 域名 `xc-api.xiachen.xyz` | 进行中 | DNS 已解析，Caddy 站点与证书待配 |
| 域名 `img.xiachen.xyz` | 进行中 | COS 自定义域名待验证 + 证书 |
| 公安联网备案 | 待办 | 网站上线 30 日内 |
| COS 存储桶 | ✅ 已创建 | `xiachenchen-images-1513506595`（ap-guangzhou），公有读私有写；密钥走环境变量 |

---

## 15. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 单机内存不足（已有 843 MB swap） | 服务被 OOM 或整体变慢 | JVM 限内存 + ZGC；监控 swap |
| 共享 MySQL 被大查询拖慢 | cost 与 resume 受影响 | 独立库 + 只读账号隔离 + 慢查询告警 |
| 库存非实时（手工维护，无 POS） | 展示有货到店无货 | 详情页标注库存更新时间 + 建议电话确认；店长每日开店核对 |
| 昵称/头像不可静默获取 | 预约称呼为空 | 改为用户主动填写 + `chooseAvatar` |
| 原生 tabBar 不支持透明 | 首页视觉无法还原 | 使用 `custom-tab-bar`，处理选中态与切页闪烁 |
| 订阅消息触达不确定 | 预约提醒发不出 | 电话兜底，订阅消息只作补充 |
| 个人备案主体用于企业小程序 | 审核抽查风险 | 中期变更为企业备案 |
| 小程序审核要求补充资质 | 上线延期 | 提前准备营业执照、类目证明、页面截图 |
| 库存缓存不一致 | 展示「有货」到店无货 | 库存 TTL ≤ 30 s + 写后即删 |
| 临时复用 cost AppID | 误提审/误发布，或数据混入 | 只设体验版；测试数据上线前清空；不提交审核 |
| 换 AppID 后 openid 全变 | 用户收藏/清单无法迁移 | 测试期不沉淀真实数据；必要时用 unionid 同主体打通 |
| 微信认证不可继承 | 新小程序需重新缴 300 元/年 | 预算与时间提前安排 |

---

## 16. 里程碑（纵切版）

| 阶段 | 范围 | 验收 |
| --- | --- | --- |
| D0–D2 | 合并文档、补数据模型、导出设计 token 与切图、打通域名/COS/证书 | 文档与仓库一致；三级域名可访问 |
| M1（5 天） | Flyway V2–V4、`/stores` `/home` `/products` `/products/{id}`、缓存、首页/列表/详情/门店切换，只跑 3 个真实商品 | 真机可看，接口 P95 < 300 ms |
| M1.5（5 天） | 后台登录、商品/库存维护、图片直传（含宽高回写）、首页大图/造型/门店封面配置 | 店长可独立录入 30 个真实 SKU |
| M2（6 天） | 登录、收藏、点赞、足迹、清单（按门店分组）、预约 | 完整用户链路可走通 |
| M3（4 天） | 预约看板、造型维护 | 店长可处理预约并维护造型 |
| M4（3 天） | 首页主题编排、监控告警、提审材料 | 运营可自主换主题 |
| 缓冲（5 天） | 设计还原、联调、真机回归 | — |

单人合计约 25–30 人日，6–7 周（不含备案等待）。**备案 20 个工作日是关键路径，需与开发并行启动。**

当前进度（2026-11-14）：D0–D2、M1、M1.5、M2、M3、M4 已完成，下一步为上线前的域名/证书、备案与提审。M1 提供 `/stores` `/home` `/products` `/products/{id}` + Redis 缓存 + 10 款种子商品；M1.5 提供后台登录（JWT + BCrypt）、商品 CRUD/上下架、图片直传（宽高回写）、SKU 整组保存、三店库存矩阵、首页大图/主题文案/造型与门店封面配置、写后失效缓存与审计留痕；M2 提供用户登录（`wx.login` → jscode2session，多 AppID + 本地 mock）、收藏/点赞、足迹（30 天）、试穿清单（按门店分组 + 预约入口）、预约到店（30 分钟档期、不扣库存、幂等），商品详情返回 `inCart` 与三店库存，小程序「我的」页新增足迹/我的预约入口；跨店调货因顾客侧价值不足已下线（V6 删除 `transfer_order`）。M3 提供后台预约看板（按日期/门店筛选、明细、确认/到店/取消，MANAGER 限本店）与造型维护（封面、排序、单品增删排序、发布前校验），React 管理端新增 `/appointments` `/looks` `/looks/new` `/looks/:id`，导航补「预约看板」「造型维护」。M4 提供首页主题编排（多主题草稿/时间窗/设为当前、模块增删排序启停、系列与门店引用校验、启用前首屏图校验，React `/home-themes` 页面）、门店资料维护（`/stores` 页可改名称/地址/电话/营业时间与封面，MANAGER 限本店，合并原「首页配置」入口，导航由「首页配置」改为「首页主题」「门店」）、健康检查脚本与企业微信告警、提审材料（`docs/release/miniprogram-submission.md`）；`/api/v1/home` 按时间窗与 `is_current` 选主题，写后即失效首页缓存。下一步是上线准备：域名/证书、备案与小程序提审。

---

## 17. 待确认

- [x] H5 后台前端已定：React 18 + Vite + TypeScript
- [ ] 图片是否需要 CDN 加速（当前直连 COS）
- [ ] 预约是否给顾客发订阅消息（需申请模板）
- [ ] 后台是否需要导出 Excel（预约、库存）
- [ ] cost AppID 的主体类型（决定手机号能力是否可用）

---

## 18. 变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v0.1 | 2026-11-14 | 首版，含多 AppID 方案 |
| v0.2 | 2026-11-14 | 合并仓库增量；新增 look 模型、多态收藏点赞、图片宽高、幂等键；补接口；里程碑改纵切；新增微信能力与库存风险 |
| v0.3 | 2026-11-14 | 目录由 `backend/` 改为 `xc/`，后台前端由 Vue 3 改为 React 18；新增 §10.1 发布与 CI/CD；字段与 `V1__schema.sql` 对齐 |
| v0.4 | 2026-11-14 | M2 收口：足迹/试穿清单/预约接口与小程序页面落地；详情增加 `inCart` 与三店 `storeStocks`；数据字典移除「未实现」标记 |
| v0.5 | 2026-11-14 | M3 收口：预约看板与造型维护落地；跨店调货下线（V6 删除 `transfer_order`，小程序与后台入口一并移除）；接口与里程碑同步删减 |
| v0.6 | 2026-11-14 | M4 收口：首页主题与模块编排、时间窗选主题、门店封面页；原「首页配置」页面与 `/api/admin/home` 下线合并；补健康检查/告警与提审材料；接口、可观测性与进度同步更新 |
| v0.7 | 2026-11-14 | 门店合并为独立「门店」页，可改资料与封面；新增 ADMIN 门店新增与上下线（`POST /stores`、`PUT /stores/{id}/status`），下线不删，C 端列表/详情/首页门店卡/预约选店同步隐藏 |
| v0.8 | 2026-11-14 | 图片存储接入腾讯云 COS（桶 `xiachenchen-images-1513506595`，域名 `img.xiachen.xyz`），上传改为后端写入；取消 `/uploads/policy` 规划 |
| v0.9 | 2026-11-14 | 移除本地磁盘回退与 `/uploads/**` 静态映射，上传只走 COS；演示数据图片迁至 COS（V7），删除 `static/seed` 本地素材 |
| v0.10 | 2026-11-14 | COS 同一桶按环境分目录：本地 `dev/`、生产 `prod/`（`COS_KEY_PREFIX`） |
| v0.11 | 2026-11-14 | 演示图片重新上传到 `dev/`（V8），数据引用从历史 `uploads/` 切换过来 |
