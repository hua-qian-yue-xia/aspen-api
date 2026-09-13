/**
 * 内部项目模块路径的唯一常量入口。
 *
 * 使用范围与规则:
 * - 各模块 build.gradle.kts 声明项目依赖时必须写 `project(AspenProjects.XXX)`,
 *   禁止在构建脚本中手写 ":aspen-..." 路径字面量;
 * - 根构建 build.gradle.kts 的架构边界守卫同样引用本对象, 保证校验与声明使用同一套路径;
 * - `settings.gradle.kts` 早于 buildSrc 编译执行, 无法引用本对象, include 列表保留字面量:
 *   新增或改名模块时必须同步 `settings.gradle.kts` 与本文件, 漏改会在编译期暴露为
 *   未知常量或不存在的项目路径。
 */
object AspenProjects {
    const val COMMON_CORE = ":aspen-common-core"

    const val COMMON_DATABASE = ":aspen-common-database"

    const val COMMON_CACHE = ":aspen-common-cache"

    const val COMMON_GATEWAY_CONTRACT = ":aspen-common-gateway-contract"

    const val COMMON_GATEWAY = ":aspen-common-gateway"

    const val COMMON_ROUTE = ":aspen-common-route"

    const val COMMON_GEN = ":aspen-common-gen"

    const val COMMON_WEB = ":aspen-common-web"

    const val COMMON_SECURITY = ":aspen-common-security"

    const val ADMIN_API = ":aspen-admin-api"

    const val ADMIN_BIZ = ":aspen-admin-biz"

    const val STORAGE_API = ":aspen-storage-api"

    const val STORAGE_BIZ = ":aspen-storage-biz"

    const val TASK_API = ":aspen-task-api"

    const val TASK_BIZ = ":aspen-task-biz"

    const val AUTH_API = ":aspen-auth-api"

    const val AUTH_BIZ = ":aspen-auth-biz"

    const val GATEWAY = ":aspen-gateway"

    /** 全仓架构边界测试的纯测试模块, 无项目依赖 */
    const val ARCHITECTURE_TEST = ":aspen-architecture-test"
}
