plugins {
    // Biz 是 Auth 唯一的 Spring Boot 运行与部署单元
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Auth unified authentication service"

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
    // 本服务对外契约: 登录端点 (AuthLoginApi) 与主体 SPI 消费 DTO
    implementation(project(AspenProjects.AUTH_API))
    // 受众路径前缀等 MVC 运行约定: controller/admin 包统一携带 /admin-api
    implementation(project(AspenProjects.COMMON_WEB))
    // JDBC 持久化服务基线: 数据源、事务管理与 Spring DAO 异常体系 (auth_session/auth_login_log)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.webmvc)
    // auth_session/auth_login_log 的 Jimmer 持久化
    implementation(project(AspenProjects.COMMON_DATABASE))
    // 客户端配置快照消费 (Store) 传递依赖 common-cache 的 Redis 分发原语
    implementation(project(AspenProjects.COMMON_SECURITY))
    // JWT 签发与 JWKS 发布 (Nimbus), 只取 jose 不引入资源服务器过滤链 (验签在网关)
    implementation(libs.spring.security.oauth2.jose)
    // 配置中心客户端: 只拉配置, optional 导入保证无 Nacos 也能启动
    implementation(libs.spring.cloud.alibaba.nacos.config)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
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
