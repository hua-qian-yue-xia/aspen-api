plugins {
    // Web 运行约定模块是普通 Library JAR, 不生成可执行 bootJar
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = rootProject.group
version = rootProject.version
description = "Aspen web MVC runtime conventions"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(platform(libs.spring.boot.dependencies))

    // 错误契约类型 (ErrorCode/BusinessException/CommonErrorCode) 来自 core,
    // core 零基础设施依赖, 因此本模块仍可被任意 MVC 服务安全引用
    implementation(project(AspenProjects.COMMON_CORE))

    // 路由套件注解定义 (GetRoute/PostRoute/RateLimitSpec/OperationTag), 消费端在本模块装配;
    // route 零项目依赖、仅 spring-web 注解 API, 不引入额外传递
    implementation(project(AspenProjects.COMMON_ROUTE))

    // 限流与操作日志的主体解析读取 security 的请求级身份上下文; compileOnly 不传递,
    // 运行期无 security 的服务 (如 task-biz) 经条件装配自动回退 XFF/IP 主体 (§8.3)
    compileOnly(project(AspenProjects.COMMON_SECURITY))

    // WebMvcConfigurer/PathMatchConfigurer 与 AntPathMatcher 只作为装配内部实现,
    // 不经 api 传递 spring-webmvc; 使用方自带 starter-webmvc, WebFlux-only 服务
    // 因类路径缺 WebMvcConfigurer 被 @ConditionalOnClass 静默退避
    implementation(libs.spring.webmvc)
    implementation(libs.spring.boot.autoconfigure)
    // Filter 的编译期 Servlet API, 运行期由使用方内嵌容器提供
    compileOnly(libs.jakarta.servlet.api)
    // 异常处理器引用 ConstraintViolationException (服务层 @Validated 校验), API 级 jar
    // 零传递依赖且随模块入运行期, 保证未引 starter-validation 的服务也能安全装载 advice
    implementation(libs.jakarta.validation.api)
    // MDC 日志关联与异常处理器日志
    implementation(libs.slf4j.api)
    // 路由套件的 OpenAPI 文档定制与运行时文档端点 (/v3/api-docs, swagger-ui);
    // implementation 随运行期变体传递给全部 biz, 生产可经 springdoc.* 配置关闭
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    // 触发真实的 @Valid 校验异常路径 (MethodArgumentNotValidException)
    testImplementation(libs.spring.boot.starter.validation)
    // 测试请求体是 Kotlin data class, 反序列化需要 Jackson Kotlin 模块 (与服务侧一致)
    testImplementation(libs.jackson.module.kotlin)
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
