plugins {
    // Biz 是 Admin 唯一的 Spring Boot 运行与部署单元
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Admin business service"

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
    implementation(project(AspenProjects.ADMIN_API))
    implementation(project(AspenProjects.COMMON_DATABASE))
    implementation(project(AspenProjects.COMMON_GEN))
    // 认证主体 SPI (AuthPrincipalApi) 的管理端实现: Admin 拥有 UPM 用户域, Auth 登录链路经本契约取主体判定
    implementation(project(AspenProjects.AUTH_API))
    // 客户端配置快照发布 (取号/守卫落盘/通知) 由 common-security 提供, Admin 只负责 sys 两表读取与行转换
    implementation(project(AspenProjects.COMMON_SECURITY))
    // 密码摘要 (BCrypt) 供主体服务比对与超管 bootstrap 建号编码, 只取 crypto 不引入安全过滤链
    implementation(libs.spring.security.crypto)

    // 当前阶段只提供可编译, 可启动的最小 MVC 运行时, 不提前引入未使用的基础设施
    implementation(libs.spring.boot.starter.webmvc)
    // 受众路径前缀等 MVC 运行约定: controller/admin|app 包统一携带 /admin-api、/app-api
    implementation(project(AspenProjects.COMMON_WEB))
    // JDBC 持久化服务基线: 数据源、事务管理 (@Transactional/事务事件) 与 Spring DAO 异常体系
    implementation(libs.spring.boot.starter.jdbc)
    // 路由快照分发等 Redis 访问一律经 common-cache 受控操作类, 业务代码不直接依赖 Redis 客户端
    implementation(project(AspenProjects.COMMON_CACHE))
    // 路由发布介质操作 (取号/信封/通知) 由 common-gateway 提供, Admin 只负责 sys_route 读取与行转换
    implementation(project(AspenProjects.COMMON_GATEWAY))
    // 配置中心客户端: 只拉配置, 注册发现暂未接入; optional 导入保证无 Nacos 也能启动
    implementation(libs.spring.cloud.alibaba.nacos.config)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    // 首个真实写路径 (字典播种) 用 Testcontainers MySQL 验证迁移与幂等
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
