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

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| Nacos 控制台 | `http://localhost:8080` | Nacos 3.x 控制台独立端口 8080 |
| Nacos 主 API | `localhost:8848` | 客户端 `server-addr` 指向这里, gRPC 走 9848 |
| MySQL | `localhost:3306` | 开发共享实例, 本轮只建 `nacos_config` 库; `nacos` 账号仅授权该库 |

9848 对宿主机暴露（IDE 直连运行的本地服务需要 gRPC）；9849/7848 等节点间端口仅在 compose 网络内开放。

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
| `aspen-common-local.yaml` | 全服务共享配置, 当前含 `aspen.routes.environment: local` |
| `aspen-admin-biz-local.yaml` | Admin 本地配置, 当前开启 `aspen.gen.dict.enabled: true`（演示分层：代码默认 false, 配置中心覆盖为 true） |
| `aspen-gateway-local.yaml` | Gateway 占位, 待网关接入配置中心后填充 |

种子策略：**只补缺失**——每次 `up` 时先 `GET /nacos/v3/admin/cs/config` 查存在，404 才 `POST` 发布；控制台手工修改不会被覆盖。`FORCE_SEED=1 docker compose ... up` 强制以 Git 文件覆盖全部 dataId。修改种子文件后想生效：删除对应 dataId 或使用 FORCE_SEED。

## 7. 客户端接线（当前仅 admin-biz）

- 依赖：`spring-cloud-starter-alibaba-nacos-config`（`implementation`，SCA BOM 管版本，`dependencyManagement` 与 Gateway 同源导入 SC/SCA BOM）。
- 导入（`application.yaml`）：

```yaml
spring:
  config:
    import:
      - optional:nacos:aspen-common-local.yaml
      - optional:nacos:aspen-admin-biz-local.yaml
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:aspen-dev-admin}
```

- `optional:` 前缀保证本地没有 Nacos 时服务照常启动（用代码内默认值），这是开发与 CI 的默认形态。
- 单元/集成测试隔离：`@SpringBootTest` 增加 `spring.cloud.nacos.config.enabled=false`、`spring.cloud.nacos.config.import-check.enabled=false`、`spring.cloud.nacos.discovery.enabled=false`，与根骨架上下文测试同模式，测试永不连接 Nacos。
- 新服务接入清单：加同款依赖与 import（公共 dataId 在前、自有 `{service}-biz-local.yaml` 在后）、`deploy/nacos/config/` 增加种子文件、`.env.example` 无需变动。

## 8. 运行验证清单（待有 Docker 的环境执行）

1. `docker compose -f deploy/docker-compose.yml up -d` 后三个容器全部 healthy/exit 0。
2. 种子日志显示三个 dataId 首次「已发布」，再次 `up` 显示「已存在跳过」。
3. 控制台 `http://localhost:8080` 用 `nacos` + `NACOS_ADMIN_PASSWORD` 登录，能看到三个配置。
4. 本地起 admin-biz（`NACOS_ADDR` 缺省即可）：日志出现 Nacos config 加载，`aspen.gen.dict.enabled` 生效为 true。
5. 停掉 Nacos 再启动 admin-biz：因 `optional:` 正常启动并回退代码默认值。

## 9. 当前验证状态（如实记录）

编写环境无 Docker daemon：已验证 compose/种子 YAML 可解析、`seed.sh` 语法、镜像 tag `nacos/nacos-server:v3.1.1` 与 API 路径均经官方文档核实、admin-biz 加依赖与 optional import 后全量 Gradle 测试通过。第 8 节的运行时验证尚未执行，首次执行后在本节记录结果并锁定镜像 digest。
