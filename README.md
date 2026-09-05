# Aspen

Aspen 是一套微服务基础设施项目，提供公共基础模块（core / database / cache）与首个业务服务 Admin（含 UPM 用户权限管理）。技术栈基于 Kotlin + Spring Boot + Gradle 多模块，持久化使用 Jimmer，缓存使用 Redis。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `aspen-common-core` | 错误码、分页等公共类型与配置 |
| `aspen-common-database` | 数据库访问封装与 Jimmer 基类 |
| `aspen-common-cache` | Redis 缓存操作封装 |
| `aspen-admin-api` | Admin 服务对外契约（普通 JAR） |
| `aspen-admin-biz` | Admin 服务唯一可运行模块 |

## 构建

```bash
./gradlew build
```

仓库地址、镜像与回退策略通过 `gradle.properties` 中的 `aspen.repository.*` 属性配置，详见 `settings.gradle.kts`。

## 文档

全部设计文档集中在 [docs/](./docs/README.md)，推荐阅读顺序：

1. [项目目标](./docs/project-goals.md)
2. [微服务技术方案](./docs/microservice-technical-solution.md)
3. [技术架构](./docs/technical-architecture.md)
4. [Common 模块设计](./docs/common-module-design.md)
5. [Admin UPM 数据模型](./docs/admin-upm-data-model.md)
