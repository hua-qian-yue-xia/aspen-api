// buildSrc 只承载构建期常量, 但 kotlin-dsl 需从仓库解析嵌入 Kotlin 的少量运行库
// 仓库策略与 settings.gradle.kts 保持一致, 权威定义在那边, 此处是等价缩小版
plugins {
    `kotlin-dsl`
}

repositories {
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
            maven {
                name = "AliyunMavenPublicMirror"
                url = uri("https://maven.aliyun.com/repository/public")
                mavenContent { releasesOnly() }
            }
        }

        mavenCentral { mavenContent { releasesOnly() } }
    }
}
