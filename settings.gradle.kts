pluginManagement {
    // 机房或 CI 有自建 Nexus/Artifactory 时, 通过 Gradle 属性或环境变量注入
    // 仓库应是同时代理 Maven Central 和 Gradle Plugin Portal 的 Maven Group 地址
    val internalRepositoryUrl = providers.gradleProperty("aspen.repository.url")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_URL"))
        .orNull
    val internalRepositoryUsername = providers.gradleProperty("aspen.repository.username")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_USERNAME"))
        .orNull
    val internalRepositoryPassword = providers.gradleProperty("aspen.repository.password")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_PASSWORD"))
        .orNull
    val useChinaMirrors = providers.gradleProperty("aspen.repository.use-china-mirrors")
        .map(String::toBoolean)
        .getOrElse(true)
    val allowPublicFallback = providers.gradleProperty("aspen.repository.allow-public-fallback")
        .map(String::toBoolean)
        .getOrElse(true)

    check(internalRepositoryUrl != null || allowPublicFallback) {
        "关闭公共仓库回退前必须配置 aspen.repository.url 或 ASPEN_MAVEN_REPOSITORY_URL"
    }
    check((internalRepositoryUsername == null) == (internalRepositoryPassword == null)) {
        "内网仓库用户名和密码必须同时配置，或同时省略"
    }

    repositories {
        internalRepositoryUrl?.let { repositoryUrl ->
            maven {
                name = "AspenInternalPluginProxy"
                url = uri(repositoryUrl)

                // 只有用户名和密码同时存在时才启用认证, 允许使用无需认证的内网仓库
                if (internalRepositoryUsername != null && internalRepositoryPassword != null) {
                    credentials {
                        username = internalRepositoryUsername
                        password = internalRepositoryPassword
                    }
                }
            }
        }

        if (allowPublicFallback) {
            if (useChinaMirrors) {
                // 阿里云的 Gradle Plugin 镜像在中国大陆通常比 Plugin Portal 稳定
                maven {
                    name = "AliyunGradlePluginMirror"
                    url = uri("https://maven.aliyun.com/repository/gradle-plugin")
                    mavenContent { releasesOnly() }
                }
            }

            // 官方仓库作为完整性回退, 避免镜像尚未同步新插件时构建被阻断
            mavenCentral { mavenContent { releasesOnly() } }
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    // 禁止各子模块私自声明仓库, 避免同一依赖从不同来源解析出不同元数据
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    val internalRepositoryUrl = providers.gradleProperty("aspen.repository.url")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_URL"))
        .orNull
    val internalRepositoryUsername = providers.gradleProperty("aspen.repository.username")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_USERNAME"))
        .orNull
    val internalRepositoryPassword = providers.gradleProperty("aspen.repository.password")
        .orElse(providers.environmentVariable("ASPEN_MAVEN_REPOSITORY_PASSWORD"))
        .orNull
    val useChinaMirrors = providers.gradleProperty("aspen.repository.use-china-mirrors")
        .map(String::toBoolean)
        .getOrElse(true)
    val allowPublicFallback = providers.gradleProperty("aspen.repository.allow-public-fallback")
        .map(String::toBoolean)
        .getOrElse(true)

    check(internalRepositoryUrl != null || allowPublicFallback) {
        "关闭公共仓库回退前必须配置 aspen.repository.url 或 ASPEN_MAVEN_REPOSITORY_URL"
    }
    check((internalRepositoryUsername == null) == (internalRepositoryPassword == null)) {
        "内网仓库用户名和密码必须同时配置，或同时省略"
    }

    repositories {
        internalRepositoryUrl?.let { repositoryUrl ->
            maven {
                name = "AspenInternalMavenProxy"
                url = uri(repositoryUrl)

                if (internalRepositoryUsername != null && internalRepositoryPassword != null) {
                    credentials {
                        username = internalRepositoryUsername
                        password = internalRepositoryPassword
                    }
                }
            }
        }

        if (allowPublicFallback) {
            if (useChinaMirrors) {
                // public 是阿里云聚合仓库, 覆盖 Maven Central 中的正式版依赖
                maven {
                    name = "AliyunMavenPublicMirror"
                    url = uri("https://maven.aliyun.com/repository/public")
                    mavenContent { releasesOnly() }
                }
            }

            // 保留官方中央仓库作为镜像同步延迟时的回退, 不配置任何 SNAPSHOT 仓库
            mavenCentral { mavenContent { releasesOnly() } }
        }
    }
}

rootProject.name = "aspen"

// Admin 是一个微服务, 由普通契约 JAR 和唯一可运行 Biz 模块组成
// 使用扁平 Gradle 项目名, services/aspen-admin 只作为源码目录分组, 不成为额外模块
// Gateway 是边缘入口, 单模块不拆 api/biz, 位于仓库根目录; 本文件先于 buildSrc 执行,
// 无法引用 AspenProjects 常量; 新增或改名模块时必须同步 buildSrc/src/main/kotlin/AspenProjects.kt
include(
    ":aspen-common-core",
    ":aspen-common-gen",
    ":aspen-common-database",
    ":aspen-common-cache",
    ":aspen-common-gateway",
    ":aspen-admin-api",
    ":aspen-admin-biz",
    ":aspen-gateway",
)
project(":aspen-common-core").projectDir = file("aspen-common/aspen-common-core")
project(":aspen-common-gen").projectDir = file("aspen-common/aspen-common-gen")
project(":aspen-common-database").projectDir = file("aspen-common/aspen-common-database")
project(":aspen-common-cache").projectDir = file("aspen-common/aspen-common-cache")
project(":aspen-common-gateway").projectDir = file("aspen-common/aspen-common-gateway")
project(":aspen-admin-api").projectDir = file("services/aspen-admin/aspen-admin-api")
project(":aspen-admin-biz").projectDir = file("services/aspen-admin/aspen-admin-biz")
project(":aspen-gateway").projectDir = file("aspen-gateway")
