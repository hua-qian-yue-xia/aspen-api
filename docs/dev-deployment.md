# 开发环境部署与 Nacos 配置中心

> 文档状态：Nacos 配置中心 compose 与 admin-biz 接线代码已实现；运行验证待有 Docker 的环境执行（见第 8 节）
> 文档基线：2026-09-06
> 关联文档：[技术架构](./technical-architecture.md)｜[微服务技术方案](./microservice-technical-solution.md)｜[Common 模块设计](./common-module-design.md)

## 1. 目标与边界

本地/开发环境一键启动 Nacos 配置中心：单节点、外部 MySQL Schema 持久化、开启鉴权、初始配置自动种子，`biz` 服务经 `spring.config.import` 拉取配置。本文件是开发环境 compose、Nacos 鉴权与种子、dataId 分层与客户端接线的权威文档；生产拓扑、备份与恢复演练规则归《微服务技术方案》§7/§16，本文只引用不重复。

组件选型结论（Nacos Client `3.1.1` 由 SCA BOM 管理、Server 镜像 `nacos/nacos-server:v3.1.1` 对齐客户端、MySQL `8.4`）见《技术架构》第 3 节，镜像 digest 待首次集成测试后锁定。

## 2. 文件清单

| 文件 | 职责 |
| --- | --- |
| `deploy/docker-compose.yml` | MySQL + Nacos + 配置种子三个容器, 健康检查驱动启动顺序 |
| `deploy/.env.example` | 全部环境变量样例; 复制为 `deploy/.env` 使用, `.env` 不入库 |
| `deploy/nacos/init/01-nacos-schema.sql` | Nacos 官方 3.1.1 MySQL Schema (alibaba/nacos Apache-2.0), 随 MySQL 首启初始化 `nacos_config` 库 |
| `deploy/nacos/seed.sh` | 种子脚本: 初始化管理员密码 → 登录取 token → 只补缺失地发布配置 |
| `deploy/nacos/config/*.yaml` | 初始 dataId 种子, 入 Git 作为初始事实源 |

## 3. 快速开始

```bash
cp deploy/.env.example deploy/.env   # 按需修改密码与密钥
docker compose -f deploy/docker-compose.yml up -d
```

启动顺序由健康状态驱动：MySQL 健康后起 Nacos，Nacos 就绪后种子容器执行一次。种子容器 `nacos-config-seeder` 是一次性任务，`docker compose logs nacos-config-seeder` 可看到每个 dataId 的「已发布 / 已存在跳过」记录。

## 4. 服务与地址

端口方案（容器内外一致, 2026-09-06 起）：MySQL=6100 / Redis=6200 / Nacos 主 API=6300。业务服务端口走 7x00 段递增：admin-biz=7100、storage-biz=7200、task-biz=7400（`application.yaml` 直配, 不进配置中心；7300 被 Nacos 客户端 gRPC 自动占用, 业务服务跳过不分配）; 6x00 段专属基础设施, 业务服务不占用。

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| Nacos 控制台 | `http://localhost:8080` | Nacos 3.x 控制台独立端口, 保持默认 8080 不入 6x00 段 |
| Nacos 主 API | `localhost:6300` | 客户端 `server-addr` 指向这里, gRPC 自动走主端口+1000 = 7300 |
| Redis | `localhost:6200` | 开发缓存实例（网关动态路由版本信封等）, AOF 持久化, 无密码仅限开发 |
| MySQL | `localhost:6100` | 开发共享实例, 当前只建 `aspen_nacos_config` 库; `nacos` 账号仅授权该库 |

7300 对宿主机暴露（IDE 直连运行的本地服务需要 gRPC）；console 8080 与节点间端口（7848 等）按默认仅在 compose 网络内开放。容器间互访用服务名 + 同端口：`mysql:6100`、`redis:6200`、`nacos:6300`。

## 5. 鉴权与密钥边界

| 变量 | 用途 | 生产要求 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | 开发 MySQL root | 必须更换, 生产数据库独立规划 |
| `NACOS_MYSQL_PASSWORD` | Nacos 专用库账号 | 必须更换 |
| `NACOS_ADMIN_PASSWORD` | Nacos 管理员与客户端账号 `nacos` 的密码, 首启由种子脚本经 `POST /nacos/v3/auth/user/admin` 初始化 | 必须更换为强密码 |
| `NACOS_AUTH_TOKEN` | 服务端 JWT 签名密钥 (base64, 原文 ≥32 字节) | 必须更换 |
| `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` | Nacos 节点间身份标识 | 必须更换 |

规则：`.env` 不入库（已加 `.gitignore`）；应用配置里只允许出现开发默认值（如 `application.yaml` 的 `${NACOS_PASSWORD:aspen-dev-admin}`），生产凭据一律经环境变量或 Docker Secret 注入，密码、私钥等敏感配置内容禁止进入 Nacos 普通配置（架构测试强制）。

## 6. 配置分层与 dataId

优先级从低到高（引用《技术架构》§13）：代码内安全默认值 < `aspen-common-local.yaml` < `aspen-admin-biz-local.yaml` < 受控环境变量/Secret。

当前 dataId（Group 固定 `DEFAULT_GROUP`，命名空间用默认 `public`，环境经 dataId 的 `-local` 后缀区分）：

| dataId | 内容 |
| --- | --- |
| `aspen-common-local.yaml` | 全服务共享配置, 当前含 `aspen.routes.environment: local` 与 `logging.pattern.level`（日志关联 MDC traceId, 配套 common-web 的 Trace ID 排查链路） |
| `aspen-admin-biz-local.yaml` | Admin 本地配置, 当前开启 `aspen.gen.dict.enabled: true`（演示分层：代码默认 false, 配置中心覆盖为 true） |
| `aspen-gateway-local.yaml` | Gateway 占位, 待网关接入配置中心后填充 |
| `aspen-storage-biz-local.yaml` | Storage 本地配置, 当前为占位种子; 数据源与 Jimmer 运行配置待服务接入数据库后填充 |
| `aspen-task-biz-local.yaml` | Task 本地配置, 当前为占位种子; 任务服务的租户来源地址、HTTP 目标白名单与 Quartz 集群配置待部署接线后填充 |

种子策略：**只补缺失**——每次 `up` 时先 `GET /nacos/v3/admin/cs/config` 查存在，404 才 `POST` 发布；控制台手工修改不会被覆盖。`FORCE_SEED=1 docker compose ... up` 强制以 Git 文件覆盖全部 dataId。修改种子文件后想生效：删除对应 dataId 或使用 FORCE_SEED。

## 7. 客户端接线（admin-biz 与 storage-biz）

- 依赖：`spring-cloud-starter-alibaba-nacos-config`（`implementation`，SCA BOM 管版本，`dependencyManagement` 与 Gateway 同源导入 SC/SCA BOM）。
- 导入（`application.yaml`，storage-biz 同款接线, 端口 7200、服务名 `aspen-storage-biz`）：

```yaml
spring:
  config:
    import:
      - optional:nacos:aspen-common-local.yaml
      - optional:nacos:aspen-admin-biz-local.yaml
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:6300}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:aspen-dev-admin}
```

- `optional:` 前缀保证本地没有 Nacos 时服务照常启动（用代码内默认值），这是开发与 CI 的默认形态。
- 单元/集成测试隔离：`@SpringBootTest` 增加 `spring.cloud.nacos.config.enabled=false`、`spring.cloud.nacos.config.import-check.enabled=false`、`spring.cloud.nacos.discovery.enabled=false`，与根骨架上下文测试同模式，测试永不连接 Nacos。
- 新服务接入清单：加同款依赖与 import（公共 dataId 在前、自有 `{service}-biz-local.yaml` 在后）、`deploy/nacos/config/` 增加种子文件、`.env.example` 无需变动。

## 8. 运行验证清单

2026-09-06 首次真实执行结果：

1. ✅ `docker compose -f deploy/docker-compose.yml up -d` 后 mysql/nacos healthy、seeder exit 0。
2. ✅ 种子日志首次三个 dataId「已发布」，再次 `up` 全部「已存在跳过」。
3. ✅ API 登录（`nacos` + `NACOS_ADMIN_PASSWORD`）取到 accessToken, 三个配置可鉴权读取; 控制台 `http://localhost:8080` 使用同一凭据, 请自行打开确认界面。
4. ⬜ 本地起 admin-biz 拉取配置——admin-biz 尚无数据源配置, 服务本身还不能完整运行, 此项与第 5 项待数据库接线完成后执行。
5. ⬜ 停掉 Nacos 验证 `optional:` 回退——同上。

## 9. 当前验证状态与运维备注

首次运行验证于 2026-09-06 完成（1-3 项), 三个镜像 digest 已锁定进 compose。执行中发现并处理的问题：

- Nacos 连 MySQL 报 `Public Key Retrieval is not allowed`——MySQL 8 `caching_sha2_password` 下非 SSL 连接必须允许公钥检索, compose 已通过 `MYSQL_SERVICE_DB_PARAM` 固化 `allowPublicKeyRetrieval=true` 等参数。
- 拉镜像依赖宿主机 Docker daemon 的网络: 本机曾因 Docker Desktop 手动代理指向未运行的 7890 端口而全部失败, 已改为跟随系统代理并为 daemon 配置 `registry-mirrors`(daocloud); 属宿主机配置, 不在本仓库文件中, 换机时需重做。
- 两个 testcontainers 集成测试（字典播种、路由分发）随 Docker 可用首次真实执行并全部通过。
