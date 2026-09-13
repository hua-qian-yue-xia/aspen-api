import org.gradle.api.artifacts.ProjectDependency

// 路线图第 12 步已落地: 根模块只保留聚合构建与全仓架构守卫,
// 不再承载应用源码与运行依赖, 各运行单元由自身模块独立构建 bootJar
group = "com.zax"
version = "0.0.1"
description = "Aspen microservice infrastructure"

// 任何运行模块都不能引入第二套 ORM, 分布式事务或 Dubbo RPC 栈
val forbiddenDependencyGroups = setOf(
    "org.hibernate.orm",
    "jakarta.persistence",
    "org.mybatis",
    "org.mybatis.spring.boot",
    "com.baomidou",
    "io.seata",
    "org.apache.seata",
    "org.apache.dubbo",
)

// 通过物理目录识别服务模块 (services/ 业务服务与 platform/ 平台服务), 避免依赖模块名称字符串推断边界
val serviceProjectPaths = subprojects
    .filter { subproject ->
        val projectDirPath = subproject.projectDir.toPath()
        // common 模块对业务服务与平台服务同等禁依赖, 二者必须同时纳入服务边界
        projectDirPath.startsWith(rootDir.toPath().resolve("services")) ||
            projectDirPath.startsWith(rootDir.toPath().resolve("platform"))
    }
    .map { it.path }
    .toSet()

allprojects {
    val sourceProjectPath = path

    configurations.configureEach {
        // 项目依赖在声明阶段校验, 即使模块没有源码也不能绕过架构边界
        dependencies.withType<ProjectDependency>().configureEach {
            val targetProjectPath = path

            // core 与 gateway-contract 是零项目依赖的协议基座, 不得依赖任何项目模块
            require(
                sourceProjectPath !in setOf(AspenProjects.COMMON_CORE, AspenProjects.COMMON_GATEWAY_CONTRACT),
            ) {
                "$sourceProjectPath 不能依赖项目模块 $targetProjectPath"
            }
            // common 基础设施模块的项目依赖白名单: database/cache/gen/web 只认 core;
            // gateway 只认 core/cache/gateway-contract, 不依赖 database (common-module-design §1);
            // security 承载业务进程最小信任链, 依赖 core (头常量) 与 database (TenantContextSupplier),
            // 无 Redis 依赖 (快照分发 SDK 已随端配置回归 Auth 本库直读移除)
            val commonProjectDependencyWhitelist = mapOf(
                AspenProjects.COMMON_DATABASE to setOf(AspenProjects.COMMON_CORE),
                AspenProjects.COMMON_CACHE to setOf(AspenProjects.COMMON_CORE),
                AspenProjects.COMMON_GEN to setOf(AspenProjects.COMMON_CORE),
                AspenProjects.COMMON_WEB to setOf(AspenProjects.COMMON_CORE),
                AspenProjects.COMMON_GATEWAY to setOf(
                    AspenProjects.COMMON_CORE,
                    AspenProjects.COMMON_CACHE,
                    AspenProjects.COMMON_GATEWAY_CONTRACT,
                ),
                AspenProjects.COMMON_SECURITY to setOf(
                    AspenProjects.COMMON_CORE,
                    AspenProjects.COMMON_DATABASE,
                ),
            )
            commonProjectDependencyWhitelist[sourceProjectPath]?.let { allowedProjectPaths ->
                require(targetProjectPath in allowedProjectPaths) {
                    "$sourceProjectPath 只能依赖 ${allowedProjectPaths.joinToString()}, 当前依赖为 $targetProjectPath"
                }
            }
            require(
                !sourceProjectPath.startsWith(":aspen-common-") || targetProjectPath !in serviceProjectPaths,
            ) {
                "common 模块不能依赖服务模块 $targetProjectPath"
            }
            if (sourceProjectPath.endsWith("-api")) {
                require(targetProjectPath !in setOf(AspenProjects.COMMON_DATABASE, AspenProjects.COMMON_CACHE)) {
                    "API 模块不能依赖数据库或缓存实现模块 $targetProjectPath"
                }
            }
        }

        // 对直接第三方依赖执行模块级白名单校验, 防止编译前已经污染边界
        dependencies.configureEach {
            val dependencyGroup = group.orEmpty()
            val dependencyName = name

            // core 与 gateway-contract 同为纯契约模块: 编译类路径禁止 Spring、Jimmer、
            // Jackson 与 Redis, 保证可被 api 安全引用 (《技术架构》7.9 与 §8)
            if (
                sourceProjectPath == AspenProjects.COMMON_CORE ||
                sourceProjectPath == AspenProjects.COMMON_GATEWAY_CONTRACT
            ) {
                require(
                    !dependencyGroup.startsWith("org.springframework") &&
                        !dependencyGroup.startsWith("org.babyfish.jimmer") &&
                        !dependencyGroup.startsWith("com.fasterxml.jackson") &&
                        !dependencyGroup.startsWith("tools.jackson") &&
                        !dependencyGroup.contains("redis") &&
                        !dependencyName.contains("redis", ignoreCase = true),
                ) {
                    "$sourceProjectPath 不能依赖基础设施库 $dependencyGroup:$dependencyName"
                }
            }
            if (sourceProjectPath.endsWith("-api")) {
                require(
                    !dependencyGroup.startsWith("org.babyfish.jimmer") &&
                        !dependencyGroup.startsWith("org.springframework.data") &&
                        !dependencyGroup.contains("redis") &&
                        !dependencyName.contains("redis", ignoreCase = true) &&
                        !dependencyName.startsWith("spring-boot-starter"),
                ) {
                    "$sourceProjectPath 不能依赖运行时基础设施 $dependencyGroup:$dependencyName"
                }
            }
            // Redis 客户端只能由 common-cache 声明并经其受控操作类暴露,
            // 其余模块 (含 gateway 的运行时组合) 一律经 common-cache 访问 Redis
            if (
                dependencyName == "spring-boot-starter-data-redis" &&
                sourceProjectPath != AspenProjects.COMMON_CACHE
            ) {
                require(false) {
                    "$sourceProjectPath 不能直接依赖 spring-boot-starter-data-redis, 请改为依赖 ${AspenProjects.COMMON_CACHE}"
                }
            }
        }

        resolutionStrategy {
            // 禁止动态版本, 变化模块和未缓存的易变解析结果, 保证 CI 与开发机可复现
            failOnNonReproducibleResolution()

            // 将架构文档中的 '唯一 ORM / 不使用 Seata, Dubbo' 落实为依赖解析硬约束
            // hibernate-validator 是 Bean Validation 实现, 不属于 Hibernate ORM, 因此不在禁用范围
            eachDependency {
                val isJpa = requested.group == "org.springframework.data" && requested.name == "spring-data-jpa"

                require(requested.group !in forbiddenDependencyGroups && !isJpa) {
                    "架构禁止依赖 ${requested.group}:${requested.name}; 数据访问只允许 Jimmer"
                }
            }
        }
    }
}
