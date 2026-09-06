import org.gradle.api.artifacts.ProjectDependency

plugins {
    // 所有插件版本集中在 gradle/libs.versions.toml, 后续子模块必须复用同一基线
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.zax"
version = "0.0.1"
description = "Aspen microservice infrastructure"

java {
    toolchain {
        // 编译与运行统一使用 Java 21, 避免依赖开发机当前 JAVA_HOME 的版本
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        // Spring Cloud 与 Spring Cloud Alibaba 的组件版本必须由正式 BOM 统一管理
        // 不在单个依赖上覆盖 Nacos, Sentinel, OpenFeign 或 RocketMQ Client 版本
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
        mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:${libs.versions.springCloudAlibaba.get()}")
    }
}

dependencies {
    // 当前根模块是迁移期的业务服务验证骨架, 因此只启用 MVC 运行时
    // Gateway 的 WebFlux 依赖已登记在版本目录中, 等 aspen-gateway 模块创建后单独引入
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aspectj)

    // 安全基线: 业务进程负责验证可信身份并建立只读安全上下文
    // Token 签发和完整认证流程仍归后续 aspen-auth-biz, 不在普通业务服务重复实现
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    // 服务注册, 配置, 同步调用和流量治理均由 Cloud/SCA BOM 管理版本
    implementation(libs.spring.cloud.starter.openfeign)
    implementation(libs.spring.cloud.starter.loadbalancer)
    implementation(libs.spring.cloud.alibaba.nacos.discovery)
    implementation(libs.spring.cloud.alibaba.nacos.config)
    implementation(libs.spring.cloud.alibaba.sentinel)
    implementation(libs.sentinel.datasource.nacos)

    // Redis 只承担缓存, 幂等和短期状态, 不作为权威业务数据库
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.data.redis)

    // RocketMQ 使用 Spring Cloud Alibaba 官方 Binder, 业务消息必须遵守事件版本和幂等规范
    implementation(libs.spring.cloud.alibaba.stream.rocketmq)

    // Jimmer 是项目唯一 ORM, KSP 在编译期生成类型安全的元模型和 DTO 实现
    implementation(libs.jimmer.spring.boot.starter)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    // Kotlin 反射和 Jackson Kotlin 模块用于 Spring 构造器绑定与 JSON 序列化
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // Boot 4 将测试能力拆成更细的 Starter, 只引入当前 MVC 与安全测试需要的部分
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Quartz 只允许由未来的 aspen-task-biz 使用, 普通业务模块不得在此处引入
    // Gateway, Quartz 和 API 契约依赖别名均已预置在 gradle/libs.versions.toml
}

kotlin {
    compilerOptions {
        // 严格处理 JSR-305 可空性, 并让无显式 use-site target 的注解同时作用于参数和属性
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

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

// 通过物理目录识别服务模块, 避免依赖模块名称字符串推断边界
val serviceProjectPaths = subprojects
    .filter { it.projectDir.toPath().startsWith(rootDir.toPath().resolve("services")) }
    .map { it.path }
    .toSet()

allprojects {
    val sourceProjectPath = path

    configurations.configureEach {
        // 项目依赖在声明阶段校验, 即使模块没有源码也不能绕过架构边界
        dependencies.withType<ProjectDependency>().configureEach {
            val targetProjectPath = path

            require(sourceProjectPath != AspenProjects.COMMON_CORE) {
                "$sourceProjectPath 不能依赖项目模块 $targetProjectPath"
            }
            if (sourceProjectPath in setOf(AspenProjects.COMMON_DATABASE, AspenProjects.COMMON_CACHE, AspenProjects.COMMON_GEN)) {
                require(targetProjectPath == AspenProjects.COMMON_CORE) {
                    "$sourceProjectPath 只能依赖 ${AspenProjects.COMMON_CORE}, 当前依赖为 $targetProjectPath"
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

            if (sourceProjectPath == ":aspen-common-core") {
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
            // 其余模块 (含 gateway 的运行时组合) 一律经 common-cache 访问 Redis;
            // 根模块是路线图第 12 步待移除的迁移期骨架, 移除前暂时豁免
            val isLegacyRootSkeleton = sourceProjectPath == ":"
            if (
                dependencyName == "spring-boot-starter-data-redis" &&
                sourceProjectPath != AspenProjects.COMMON_CACHE &&
                !isLegacyRootSkeleton
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
