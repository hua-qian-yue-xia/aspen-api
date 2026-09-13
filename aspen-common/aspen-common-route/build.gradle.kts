plugins {
    // 路由套件注解契约是普通 Library JAR, 不生成可执行 bootJar, 无任何 Bean 与自动装配
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen route suite annotation contracts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // API 不应用 Boot 插件, 只借用其版本平台对齐 spring-web (及传递的 spring-core) 版本
    api(platform(libs.spring.boot.dependencies))

    // 路由套件只依赖 Spring Web 的协议注解 (RequestMapping/RequestMethod) 与
    // spring-core 的 @AliasFor (随 spring-web 传递), 零项目依赖、零运行时基础设施,
    // 因此可被任意 api 契约模块安全引用; 消费端全部在 aspen-common-web (§8.3)
    api(libs.spring.web)

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
