# 夏晨衣舍 · 开发设计文档

- 版本：v0.1
- 日期：2026-11-14
- 状态：待评审
- 关联需求：[夏晨衣舍女装 · 微信小程序 需求明细 v0.5](https://app.notion.com/p/418a36e1ae8a81f3b57df85ba430b267)
- 原型：Ardot 高保真原型（14 屏）

---

## 1. 背景与目标

夏晨衣舍（原夏晨晨）是经营 12 年的深圳女装品牌，3 家门店（罗湖 / 福田 / 盐田）。本系统建设微信小程序与 H5 管理后台，**不做线上交易**，核心链路是「浏览 → 收藏/点赞 → 试穿清单 → 预约到店」，并提供跨店调货能力。

### 目标

1. 顾客免登录浏览三店在售商品，登录后可收藏、点赞、加清单、预约、申请调货。
2. 店长在手机浏览器上维护商品、图片、尺码、库存、门店信息与预约。
3. 首页、上新、商品、库存全部可配置，不需要为内容变更发版。
4. 系统在单台 3.6 GB 内存的云服务器上与 cost、resume 共存。

### 非目标

- 在线支付、订单、物流、退款、评价、销量
- 会员积分、优惠券、分销
- 跨店调拨的物流追踪（只记录到店状态）
- 多语言、多品牌

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
        ├──────── MySQL 8 ─────────┤   xiachen_yishe 库（复用现有实例）
        ├──────── Redis ───────────┤   db2 / db3（复用现有实例）
        └──────── COS ─────────────┘   img.xiachen.xyz（图片）
```

### 2.1 服务划分

| 服务 | 端口 | 职责 | JVM |
| --- | --- | --- | --- |
| `xc-api` | 8081 | 首页、商品、收藏、点赞、足迹、试穿清单、预约、调货、微信登录 | `-Xmx384m` |
| `xc-admin` | 8082 | 商品与图片维护、库存、门店、系列与首页主题、预约与调货看板、账号权限 | `-Xmx256m` |

两个服务**共用同一个 MySQL 库与 Redis 实例**。这是有意的折中：以受众为边界（顾客 / 店长），而不是按业务域拆；当前规模下拆更多只会增加运维成本，不产生收益。

### 2.2 边界约定

- `xc-api` 只读业务数据，写操作限于用户行为（收藏、点赞、足迹、清单、预约、调货意向）。
- `xc-admin` 是唯一的商品与库存写入口。
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
| 对象存储 | 腾讯云 COS | 图片直传，自定义域名 `img.xiachen.xyz` |
| 构建 | Maven 多模块 + Docker | 本地构建镜像，服务器只运行 |

---

## 4. 工程结构

```
xiachen-yishe/
├── backend/
│   ├── pom.xml                  聚合
│   ├── xc-common/               通用：异常、响应体、缓存封装、鉴权、审计
│   ├── xc-domain/               实体、Mapper、领域服务（两服务共用）
│   ├── xc-api/                  C 端应用（port 8081）
│   └── xc-admin/                后台应用（port 8082）
├── admin-web/                   H5 后台前端（Vue 3 + Vite）
├── miniprogram/                 微信小程序（原生 JS/WXML）
├── deploy/
│   ├── docker-compose.yml
│   ├── caddy/                   站点配置片段
│   └── sql/                     仅放只读说明，实际迁移走 Flyway
└── docs/
    └── superpowers/specs/       设计与计划
```

约定：`xc-domain` 被两个应用依赖，**禁止**在 `xc-domain` 里引入 Web 依赖。

### 4.1 多小程序 AppID 支持

开发期临时复用 cost 的 AppID（`wxfef7698185824d972`）做体验版演示，正式上线再换成新的夏晨衣舍 AppID。为避免上线时改代码，登录链路从一开始就按「多 AppID」设计：

```yaml
wechat:
  apps:
    - appid: wxfef7698185824d972
      secret: ${COST_APPSECRET}
      label: cost
    - appid: wx????????????????
      secret: ${XC_APPSECRET}
      label: xichen
```

- 请求头携带 `X-App-Id`，或由网关按 Host 映射到具体 AppID
- `app_user` 表记录 `appid`，openid 与 appid 联合唯一
- 未注册的 AppID 先用占位符，注册后只改配置，不改代码

---

## 5. 数据模型

库名 `xiachen_yishe`，字符集 `utf8mb4`，引擎 InnoDB。

### 5.1 门店与人员

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `store` | id, name, code, address, phone, open_time, close_time, lat, lng, cover_url, status | 三家门店 |
| `staff` | id, store_id, username, password_hash, role(ADMIN/MANAGER), openid, status | 后台账号；ADMIN 可跨店 |
| `audit_log` | id, staff_id, action, target_type, target_id, detail, created_at | 后台操作留痕 |

### 5.2 商品

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `product` | id, title, subtitle, category, season, material, description, cover_url, status(DRAFT/ON/OFF), sort_weight, created_at, updated_at | SPU，全局共享 |
| `product_image` | id, product_id, url, sort_no | 多图，主图取 sort_no 最小 |
| `sku` | id, product_id, color, size, price_cent, origin_price_cent, status | 颜色 × 尺码 |
| `inventory` | id, store_id, sku_id, quantity, on_sale, updated_at | **按门店**；唯一键 (store_id, sku_id) |
| `collection` | id, name, cover_url, story, publish_at, status | 系列，如 AW26 秋冬 |
| `collection_product` | collection_id, product_id, sort_no | 系列内商品与排序 |

金额统一用 `int` 存**分**，禁止浮点。

### 5.3 首页

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `home_theme` | id, name, cover_url, copy, start_at, end_at, status, current | 多套主题 |
| `home_module` | id, theme_id, type(BANNER/COLLECTION/RECOMMEND/STORE), ref_id, image_url, sort_no, enabled | 模块编排 |

`current` 标记当前生效主题；定时切换由查询时按 `start_at/end_at` 判定，不依赖定时任务。

### 5.4 用户与行为

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `app_user` | id, appid, openid, unionid, nickname, phone, avatar_url, created_at | 微信用户；唯一键 (appid, openid) |
| `user_favorite` | id, user_id, product_id, created_at | 唯一键 (user_id, product_id) |
| `user_like` | id, user_id, product_id, created_at | 与收藏分表，语义不同 |
| `user_footprint` | id, user_id, product_id, store_id, viewed_at | 保留 30 天，定时清理 |
| `tryon_item` | id, user_id, sku_id, store_id, created_at | 试穿清单 |
| `appointment` | id, user_id, store_id, visit_date, visit_time, item_count, phone, contact_name, remark, status(PENDING/CONFIRMED/DONE/CANCELED), created_at | 预约到店 |
| `appointment_item` | appointment_id, sku_id, store_id | 预约包含的商品 |

### 5.5 调货

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `transfer_order` | id, user_id, sku_id, from_store_id, to_store_id, phone, remark, status(PENDING/IN_TRANSIT/ARRIVED/NOTIFIED/CANCELED), created_at, updated_at | 顾客发起的调货意向 |

索引要求：`user_favorite(user_id, created_at)`、`user_footprint(user_id, viewed_at)`、`inventory(store_id, sku_id)`、`product(status, category, sort_weight)`。

---

## 6. 接口设计

统一返回 `{code, message, data}`，`code=0` 表示成功。分页参数 `page`、`size`（默认 20，上限 50）。

### 6.1 C 端 `/api/v1`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/wx-login` | code 换 openid，返回 JWT |
| POST | `/auth/phone` | 手机号解密绑定 |
| GET | `/home` | 当前主题 + 模块 + 门店胶囊数据（可带 storeId） |
| GET | `/products` | 列表：category、sort、onlyStock、color、size、material、storeId、keyword |
| GET | `/products/{id}` | 详情 + 三店库存 + 用户状态（收藏/点赞） |
| GET | `/stores`、`/stores/{id}` | 门店列表与详情 |
| GET/POST/DELETE | `/favorites` | 收藏列表、添加、取消 |
| GET/POST/DELETE | `/likes` | 点赞列表、添加、取消 |
| GET/DELETE | `/footprints` | 足迹（默认 50 条，30 天） |
| GET/POST/PATCH/DELETE | `/tryon-items` | 试穿清单增删改查 |
| POST | `/appointments` | 预约到店 |
| GET | `/appointments` | 我的预约 |
| POST | `/transfers` | 发起调货 |

### 6.2 后台 `/api/admin/v1`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 账号密码登录 |
| CRUD | `/products` | 商品与 SKU、图片 |
| PATCH | `/inventory` | 单条或批量改库存 / 在售 |
| GET/PATCH | `/stores/{id}` | 门店信息 |
| CRUD | `/collections` | 系列 |
| CRUD | `/home-themes` | 首页主题与模块 |
| GET/PATCH | `/appointments` | 预约看板（确认 / 完成 / 取消） |
| GET/PATCH | `/transfers` | 调货单状态流转 |
| POST | `/uploads/policy` | 返回 COS 直传临时凭证 |
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

### 7.2 预约到店

1. 清单勾选商品 → 打开预约浮层
2. 选门店、日期、时间（30 分钟一档，可不填）、手机号、称呼、备注
3. `POST /appointments`：写 `appointment` + `appointment_item`，**不做库存扣减、不设人数上限**
4. 后台看板确认后，小程序「我的预约」显示状态

重复提交由前端 `client_request_id` 幂等（后端唯一键兜底）。

### 7.3 跨店调货

1. 详情页当前门店售罄 → 展示其他有货门店
2. 选来源店 + 留手机号 → `POST /transfers`
3. 后台把状态从 `PENDING` 推到 `IN_TRANSIT` → `ARRIVED` → `NOTIFIED`
4. **不锁定库存**；`NOTIFIED` 后由门店电话或订阅消息通知顾客

---

## 8. 缓存设计

| 数据 | Key | TTL | 失效 |
| --- | --- | --- | --- |
| 首页主题 | `xc:home:{storeId}` | 10 min | 后台发布主题时删 |
| 商品详情 | `xc:product:{id}` | 5 min | 编辑商品时删 |
| 列表结果 | `xc:list:{storeId}:{sha1(params)}` | 60 s | 商品/库存变更按前缀删 |
| 库存 | `xc:stock:{storeId}:{skuId}` | 30 s | 写操作即时更新 |
| 收藏/点赞数 | `xc:count:fav:{id}` / `xc:count:like:{id}` | 10 min | 计数递增 + 异步校正 |
| 后台会话 | `xca:sess:{token}` | 8 h | 登出删除 |

防护：

- 穿透：不存在的 id 缓存空值 60 s
- 击穿：热点 key 互斥锁（Redis `SET NX PX`）
- 雪崩：TTL 随机抖动 ±20%
- 约定：单 key ≤ 50 KB，禁止 `KEYS`，集合用 `SSCAN`

Redis 实例级设置：`maxmemory 512mb`、`maxmemory-policy volatile-lru`、缓存实例关闭 RDB/AOF。

---

## 9. 图片与上传

- 后台上传走 **COS 直传**：`POST /uploads/policy` 返回临时凭证，浏览器直接 PUT
- 命名：`products/{productId}/{uuid}.jpg`，封面 `themes/{themeId}/cover.jpg`
- 规格：原图 ≤ 2000px 宽；列表图 380px；详情图 760px，WebP，质量 75
- 存储桶：**公有读私有写**（图片是小程序公开展示内容）
- 域名：`img.xiachen.xyz`（COS 自定义域名 + HTTPS 证书）

---

## 10. 部署

```
docker-compose.services:
  xc-api     :8081  mem_limit 768m  -Xmx384m ZGC
  xc-admin   :8082  mem_limit 512m  -Xmx256m ZGC
  （复用）cost-cache-redis、cost-cloud-mysql
```

Caddyfile 片段：

```caddy
xc-api.xiachen.xyz {
    reverse_proxy 127.0.0.1:8081
}
admin.xiachen.xyz {
    root * /srv/admin-web
    try_files {path} /index.html
    file_server
    handle /api/* {
        reverse_proxy 127.0.0.1:8082
    }
}
```

健康检查：`/actuator/health`（仅内网可访问，Caddy 不对外暴露）。

构建：本地/CI 产出 `linux/amd64` 镜像，服务器 `docker compose pull && up -d`，**不在服务器上编译**。

---

## 11. 可观测性与运维

- 日志：JSON 行格式，输出到 stdout，由 Docker 收集；保留 7 天
- 慢 SQL：MySQL `slow_query_log`，阈值 200 ms
- 指标：Actuator + Micrometer，暴露 JVM、连接池、缓存命中率
- 备份：每日 `mysqldump --single-transaction` 压缩后上传 COS，保留 30 天

---

## 12. 数据迁移与初始化

Flyway 版本规划：

- `V1__schema.sql`：建表与索引
- `V2__seed_store.sql`：三家门店
- `V3__seed_admin.sql`：初始管理员（密码从环境变量注入，禁止明文入库）

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
| 微信小程序注册 | 待办 | 企业主体，营业执照，名称「夏晨衣舍」 |
| 新 AppID 域名重配 | 待办 | 新 AppID 需重新填写 request/uploadFile/downloadFile |
| 小程序备案 | 待办 | 2023 年起必须，平台提交管局，约 20 个工作日 |
| 微信认证 | 待办 | 300 元/年，服饰内衣类目发布通常需要 |
| 服务类目 | 待办 | 服饰内衣 / 商家自营 |
| 域名 `xc-api.xiachen.xyz` | 进行中 | DNS 已解析，Caddy 站点与证书待配 |
| 域名 `img.xiachen.xyz` | 进行中 | COS 自定义域名待验证 + 证书 |
| 公安联网备案 | 待办 | 网站上线 30 日内 |
| COS 存储桶 | 待办 | 公有读私有写 + 证书 |

---

## 15. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 单机内存不足（已有 843 MB swap 使用） | 服务被 OOM 或整体变慢 | JVM 限内存 + ZGC；后台不常驻内存大对象；监控 swap |
| 共享 MySQL 被大查询拖慢 | cost 与 resume 受影响 | 独立库 + 只读账号隔离 + 慢查询阈值告警 |
| 个人备案主体用于企业小程序 | 审核抽查风险 | 中期变更为企业备案 |
| 小程序审核要求补充资质 | 上线延期 | 提前准备营业执照、类目证明、页面截图 |
| 库存缓存不一致 | 展示「有货」到店无货 | 库存 TTL ≤ 30 s + 写后即删 |
| 临时复用 cost AppID | 误提审/误发布，或数据混入 | 只设体验版；测试数据上线前清空；不提交审核 |
| 换 AppID 后 openid 全变 | 用户收藏/清单无法迁移 | 测试期不沉淀真实数据；必要时用 unionid 同主体打通 |
| 微信认证不可继承 | 新小程序需重新缴 300 元/年并重新认证 | 预算与时间提前安排 |

---

## 16. 里程碑

| 阶段 | 范围 | 验收 |
| --- | --- | --- |
| M1 | 表结构、门店/商品读接口、缓存、小程序浏览链路 | 小程序可看首页与商品，接口 P95 < 300 ms |
| M2 | 登录、收藏、点赞、足迹、试穿清单、预约、调货 | 完整用户链路可走通 |
| M3 | 后台登录、商品与库存维护、图片直传、预约与调货看板 | 店长可独立维护，不需要改代码 |
| M4 | 首页主题编排、数据看板、监控告警 | 运营可自主换主题 |

---

## 17. 待确认

1. H5 后台前端框架是否用 Vue 3（与小程序原生并存）？
2. 图片是否需要 CDN 加速（当前直连 COS）？
3. 预约是否需要给顾客发订阅消息（需申请模板）？
4. 后台是否需要导出 Excel（预约、库存）？
