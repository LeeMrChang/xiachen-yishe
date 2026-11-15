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
| xc-api / xc-admin | Java verify → 隔离 MySQL/Redis 启动与系列业务测试 → 源码小包 → 腾讯云服务器构建 → 版本、健康与业务检查 |
| web | npm ci、类型检查和构建 → 静态文件上传 → 原子切换 current 链接 |
| miniprogram | JavaScript/JSON 校验；不自动上传微信、不自动提审 |
| scripts | 录入脚本语法与数据引用校验、失败退出测试、部署传输与回滚测试 |

共享模块、Compose、部署脚本变更会同时发布两个后端。部署 job 仅在 `DEPLOY_ENABLED=true` 且 main 分支执行。
后端 PR 执行构建与隔离数据库冒烟测试，不推送镜像、不部署。两个服务并行构建，生产部署共用 `xiachen-backend-production` 并发组，从上传开始串行执行，不取消正在发布的服务。每个服务同一分支只允许一个活跃发布；连续推送时，GitHub 默认保留最新的待执行工作流。

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

GitHub Actions 验证源码后只打包后端 POM、Dockerfile、构建设置和 `src/main`，上传约 67 KB 的源码包及 SHA-256。不会上传 `.env`、`application-local.yml`、本机 target 或图片。服务器验证归档白名单、路径、校验和与源码 SHA，然后在 Docker 内构建镜像，不在宿主机安装 Maven/Java。

服务器使用腾讯云 Maven/JRE 镜像与 Maven 仓库，先按 POM 缓存构建依赖层，再复制源码编译。构建串行执行，限制单次 1 CPU / 1 GiB；开始前要求至少 1536 MiB 可用内存、4 GiB 空闲磁盘。首次建立依赖缓存可能较慢；上传、资源不足或构建失败均不替换运行中的服务。

部署分为源码上传、云端构建、启动验收三步。整个部署 job 为 65 分钟，包含最多 40 分钟冷构建和 15 分钟启动检查/回滚预算。镜像沿用 `.env` 中现有仓库前缀和完整 SHA 标签，在服务器本地构建；现有 `ghcr.io/...` 前缀只用于兼容镜像命名，发布不再依赖 GHCR 推拉。默认不向腾讯云 CCR 推送，需要归档时再单独配置夏晨的仓库与凭据。

启动后核对运行容器镜像 ID 与 revision；API 验证首页响应，admin 验证未登录访问系列接口仍返回 401。系列写入流程只在 CI 的隔离数据库验证。SSH 校验主机密钥并设置连接超时与保活。

需要排查历史镜像传输时，可手动运行 `deploy-diagnostics` 并填写完整发布 SHA。默认只读取传输进程、归档大小、SSH 计数和健康状态。可选 `registry_pull` 用当前 job 的短期 token 测试历史 GHCR API 镜像拉取，凭据目录退出时清理，不重启服务。

服务器配置位于 `/home/ubuntu/xiachen-yishe/deploy/.env`，权限 600，模板见 `deploy/.env.example`。
部署脚本会同步 Compose，并在版本、健康和业务检查成功后记录镜像 SHA。启动或验收失败会尝试恢复上个容器镜像；首次发布失败则停止失败容器。**数据库迁移不自动回滚**，发布破坏性迁移前应备份数据库。

管理网页使用 `releases/<sha>` 与相对 `current` 软链接；不使用 rsync --delete 覆盖现有公共网站。
共享 Caddy 当前挂载的站点目录变更时，需要相应更新 `DEPLOY_WEB_ROOT` 和网关挂载；不要让其他项目发布删除夏晨衣舍目录。

## 数据迁移

Flyway 按环境隔离迁移，API 与管理服务保持一致：

- 共用结构：`classpath:db/migration`。
- 生产默认：`db/migration` + `db/production`；生产 V7 清理门店本机封面地址。
- 本地 local：`db/migration` + `db/demo`；保留已执行的演示 V4、V7、V8。

历史上生产和演示各有一个 V7，不能同时扫描 `db/production` 与 `db/demo`。已执行脚本的版本、文件名和 SQL 内容保持不变，不手改迁移记录。后续共用迁移从 **V9** 起统一分配版本号，并同时检查两套路径是否重号。
CI 会通过 `python3 deploy/scripts/check-migrations.py` 同时检查生产、本地两套迁移的版本唯一性。
修改迁移资源位置后，运行 `mvn -f xc/pom.xml clean verify`，清除 target 中旧路径的资源。
CI 通过全新数据库启动验证迁移，并断言生产 profile 商品列表为空，避免演示数据进入生产。

## 验证与回滚

- 内网：18091/18092 `/actuator/health`；公网屏蔽 `/actuator`。
- `deploy/scripts/healthcheck.sh` 检查两个服务及首页接口；可用于 cron。
- 手动回滚：在服务器 `.env` 将对应 `XC_API_TAG` / `XC_ADMIN_TAG` 改成已验证的 SHA，确认该版本镜像仍在本机后执行 `docker compose up -d --no-deps --pull never <service>`，再检查健康。
- 前端回滚：将站点 `current` 链接切回旧 `releases/<sha>`。

## 门店数据录入

商品、造型、首页主题可以批量写入后台，不必逐条点页面：

```bash
XC_USER=admin XC_PASS=<密码> node scripts/seed-storefront.mjs all
```

数据源在 `scripts/data/storefront.json`，脚本按标题匹配、幂等可重跑，详见 `scripts/README.md`。

## 文档

设计与数据字典：`docs/superpowers/specs/`；阶段记录：`docs/superpowers/plans/`；门店数据脚本：`scripts/README.md`；提审材料：`docs/release/miniprogram-submission.md`。
