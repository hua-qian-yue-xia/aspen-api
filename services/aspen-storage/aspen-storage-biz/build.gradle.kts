plugins {
    // Biz 是 Storage 唯一的 Spring Boot 运行与部署单元
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = rootProject.group
version = rootProject.version
description = "Aspen Storage business service"

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
    implementation(project(AspenProjects.STORAGE_API))
    implementation(project(AspenProjects.COMMON_DATABASE))

    // 当前阶段只提供可编译, 可启动的最小 MVC 运行时, 不提前引入未使用的基础设施;
    // 文件存储本轮只有实体与迁移, 上传/秒传/续传的 Service 与 Controller 随下一轮补齐
    implementation(libs.spring.boot.starter.webmvc)
    // JDBC 持久化服务基线: 数据源、事务管理 (@Transactional/事务事件) 与 Spring DAO 异常体系
    implementation(libs.spring.boot.starter.jdbc)
    // 配置中心客户端: 只拉配置, 注册发现暂未接入; optional 导入保证无 Nacos 也能启动
    implementation(libs.spring.cloud.alibaba.nacos.config)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.kotlin.test.junit5)
    // 首个真实链路 (秒传唯一索引与迁移可执行性) 用 Testcontainers MySQL 验证
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
