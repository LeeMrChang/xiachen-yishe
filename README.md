# 夏晨衣舍

三家深圳女装实体门店的微信小程序与店长管理后台，不做线上交易。

## 本地开发

- Java 21、Maven、Node.js 22、MySQL 8、Redis。
- `xc/xc-api` 默认 8081；`xc/xc-admin` 默认 8082；React 后台 `web/` 默认 5173，代理 `/api` 到 8082。
- `application-local.yml` 只放本地配置，禁止提交密钥。生产显式激活 `prod`。
- `mvn -f xc/pom.xml verify`；`cd web && npm ci && npm run build`。
- 小程序 develop 使用本机接口，trial/release 自动使用 `https://xc-api.xiachen.xyz`，AppID 从微信运行环境读取。

## 自动发布

main 分支按路径触发 GitHub Actions：

| 工作流 | 行为 |
| --- | --- |
| xc-api / xc-admin | Java verify → 隔离 MySQL/Redis 启动测试 → GHCR 完整 SHA 镜像 → SSH 单服务发布 → 健康检查 |
| web | npm ci、类型检查和构建 → 静态文件上传 → 原子切换 current 链接 |
| miniprogram | JavaScript/JSON 校验；不自动上传微信、不自动提审 |

共享模块、Compose、部署脚本变更会同时发布两个后端。部署 job 仅在 `DEPLOY_ENABLED=true` 且 main 分支执行。

生产拓扑：

- Caddy `xc-api.xiachen.xyz` → 宿主机回环 18091 → xc-api 容器 8081。
- Caddy `admin.xiachen.xyz` 的 `/api/*` → 回环 18092 → xc-admin 容器 8082；其他路径读取静态网页。
- 两容器连接现有外部网络 `cost-cloud_default`，通过容器 DNS 访问 MySQL/Redis；不开放数据库公网端口。
- 独立数据库/账号 `xiachen_yishe`；Redis DB 2，`xc:` 缓存前缀。
- API 与 admin 使用不同 JWT 签名密钥，防止身份令牌互通。
- COS 使用 `prod/`；生产关闭 WX_MOCK。当前 cost AppID 仅供体验版，不提审、不替换 cost 已发布版本。

### GitHub 配置

Variable：`DEPLOY_ENABLED`。
Secrets：`DEPLOY_SSH_KEY`、`DEPLOY_KNOWN_HOSTS`、`DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_PATH`、`DEPLOY_WEB_ROOT`。

镜像由 Actions 推送 GHCR，同时打包为保留一天的部署制品，通过 SSH 传到服务器后加载；服务器无需保存 GHCR 凭据，也不依赖跨境拉取镜像。服务器保留每个服务最近一份镜像归档，后续通过 rsync 仅传输差异数据，支持中断后续传。SSH 校验已核对的主机密钥，不跳过主机认证。

服务器配置位于 `/home/ubuntu/xiachen-yishe/deploy/.env`，权限 600，模板见 `deploy/.env.example`。
部署脚本会同步 Compose，并在健康检查成功后记录镜像 SHA。启动失败会尝试恢复上个容器镜像；首次发布失败则停止失败容器。**数据库迁移不自动回滚**，发布破坏性迁移前应备份数据库。

管理网页使用 `releases/<sha>` 与相对 `current` 软链接；不使用 rsync --delete 覆盖现有公共网站。
共享 Caddy 当前挂载的站点目录变更时，需要相应更新 `DEPLOY_WEB_ROOT` 和网关挂载；不要让其他项目发布删除夏晨衣舍目录。

## 数据迁移

Flyway 正式路径 `classpath:db/migration`；演示迁移仅 local 加载 `db/demo`。新增变更追加新版本，禁止修改已执行的历史 SQL。
CI 通过全新数据库启动验证迁移，并断言生产 profile 商品列表为空，避免演示数据进入生产。

## 验证与回滚

- 内网：18091/18092 `/actuator/health`；公网屏蔽 `/actuator`。
- `deploy/scripts/healthcheck.sh` 检查两个服务及首页接口；可用于 cron。
- 手动回滚：在服务器 `.env` 将对应 `XC_API_TAG` / `XC_ADMIN_TAG` 改成已验证的 SHA，确认该版本镜像仍在本机后执行 `docker compose up -d --no-deps --pull never <service>`，再检查健康。
- 前端回滚：将站点 `current` 链接切回旧 `releases/<sha>`。

## 文档

设计与数据字典：`docs/superpowers/specs/`；阶段记录：`docs/superpowers/plans/`。
