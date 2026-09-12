plugins {
    // 纯契约 JAR: 只承载路由分发的数据模型与介质 Key 约定, 与 core 同级, 不引入任何基础设施
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = rootProject.group
version = rootProject.version
description = "Aspen gateway route distribution contracts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // 故意不引入 Spring, Jackson, Jimmer 或 Redis: 本模块必须能被 api 安全引用,
    // 零运行时依赖保证不向契约消费方传递任何基础设施 (《技术架构》7.9 与 §8);
    // 该边界由根构建的第三方依赖白名单与 common-module-design §8 验收项共同强制
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
