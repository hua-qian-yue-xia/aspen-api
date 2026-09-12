plugins {
    // 网关路由分发 SDK 是普通 Library JAR, 不生成可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen gateway route distribution protocol and SDK"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))

    // 纯契约模块以 api 暴露: 发布/消费原语与自动装配的公共构造签名不引用契约以外
    // 的契约消费方类型, 网关与 Admin 经本模块间接获得同一套信封与 Key 约定
    api(project(AspenProjects.COMMON_GATEWAY_CONTRACT))

    // Redis 访问一律经 common-cache 受控操作类, 本模块不直接暴露 Redis 客户端类型;
    // 收发原语的构造签名含 AspenRedisOperations, 因此保持 api 传递
    api(project(AspenProjects.COMMON_CACHE))

    // 自动装配与 JSON 序列化只作为内部实现, 不进入公共 API
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.slf4j.api)

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
