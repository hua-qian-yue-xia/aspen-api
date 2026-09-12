plugins {
    // Biz 是 Task 唯一的 Spring Boot 运行与部署单元 (全项目唯一允许 Quartz 的模块)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Task unified scheduling service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        // Spring Cloud 与 Spring Cloud Alibaba 的组件版本必须由正式 BOM 统一管理, 与根构建同源
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
        mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:${libs.versions.springCloudAlibaba.get()}")
    }
}

dependencies {
    implementation(project(AspenProjects.TASK_API))
    implementation(project(AspenProjects.COMMON_DATABASE))

    // 全租户投递消费 Admin 的启用租户内部契约 (UpmTenantApi 路径常量与 TenantBriefDto 结构),
    // biz -> api 是架构允许的跨服务契约依赖方向, 不链接 Admin 实现
    implementation(project(AspenProjects.ADMIN_API))

    // 受众路径前缀等 MVC 运行约定: controller/admin 包统一携带 /admin-api 前缀
    implementation(project(AspenProjects.COMMON_WEB))
    // 全租户解析的短 TTL 缓存经 common-cache 受控操作类访问, 不直接依赖 Redis 客户端
    implementation(project(AspenProjects.COMMON_CACHE))

    implementation(libs.spring.boot.starter.webmvc)
    // JDBC 持久化服务基线: 数据源、事务管理 (@Transactional/事务事件) 与 Spring DAO 异常体系
    implementation(libs.spring.boot.starter.jdbc)
    // Quartz JDBC Cluster 调度引擎: 技术架构 14.1 唯一指定, 全项目仅本模块允许引入;
    // QRTZ_* 表经本服务 V002 迁移引入, 运行时 initialize-schema 必须保持 never
    implementation(libs.spring.boot.starter.quartz)
    // 契约 DTO 的 Bean Validation 实现 (jakarta.validation 注解在 api 模块声明)
    implementation(libs.spring.boot.starter.validation)
    // 配置中心客户端: 只拉配置, 注册发现暂未接入; optional 导入保证无 Nacos 也能启动
    implementation(libs.spring.cloud.alibaba.nacos.config)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    // 迁移真实执行验证用 Testcontainers MySQL (与 Storage/Admin 同款 Docker 缺失禁用模式)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
