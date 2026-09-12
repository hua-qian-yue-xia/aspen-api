plugins {
    // 占位构建: 认证服务运行时在统一认证批次 3 落地 (Spring Boot 骨架 + 认证引擎),
    // 先以空 Library 满足 settings include 的目录存在性要求
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Auth runtime (placeholder until batch 3)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
