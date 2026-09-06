plugins {
    // Gateway 是唯一外部入口的独立 Boot 应用, 边缘入口不拆 api/biz
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen gateway edge service"

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
    // 仅消费 admin-api 的路由契约 (纯数据模型与 Redis Key 约定), 禁止链接任何业务实现
    implementation(project(AspenProjects.ADMIN_API))

    // Redis 访问一律经 common-cache 受控操作类, 业务代码不直接依赖 Redis 客户端
    implementation(project(AspenProjects.COMMON_CACHE))

    // 网关专用运行时组合, 严禁与业务 MVC/Jimmer 运行时混用
    implementation(libs.bundles.gateway.runtime)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.boot.starter.test)
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
