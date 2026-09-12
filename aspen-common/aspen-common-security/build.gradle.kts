plugins {
    // 认证配置分发 SDK 与业务进程最小信任链是普通 Library JAR, 不生成可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen auth client config distribution and minimal trust chain"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))

    // 身份与租户头常量来自 core, TenantContextSupplier 契约来自 database,
    // 均只作内部实现引用, 不进入本模块公共 API
    implementation(project(AspenProjects.COMMON_CORE))
    implementation(project(AspenProjects.COMMON_DATABASE))

    // 发布/消费原语的构造签名含 AspenRedisOperations, 因此保持 api 传递;
    // Redis 访问一律经 common-cache 受控操作类, 本模块不直接暴露 Redis 客户端类型
    api(project(AspenProjects.COMMON_CACHE))

    // 最小信任链的 Servlet 过滤器载体; 编译期 Servlet API 由使用方内嵌容器提供
    implementation(libs.spring.web)
    compileOnly(libs.jakarta.servlet.api)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    // MockHttpServletRequest 与 Filter 签名需要编译期 Servlet API (main 侧为 compileOnly 不传递)
    testImplementation(libs.jakarta.servlet.api)
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
