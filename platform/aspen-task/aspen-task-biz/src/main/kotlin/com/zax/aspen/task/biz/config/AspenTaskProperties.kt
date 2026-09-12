package com.zax.aspen.task.biz.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 绑定 Aspen Task 运行配置
 *
 * 数据源、Jimmer 与 Nacos 接线遵循服务统一约定; 本配置承载任务服务特有的
 * HTTP 目标校验、租户来源、投递线程池与保留期治理参数, 逐项给出安全默认值,
 * 生产环境经 Nacos 的 aspen-task-biz 覆盖
 */
@ConfigurationProperties("aspen.task")
class AspenTaskProperties {
    /** HTTP 投递的公共约束与目标校验白名单 */
    var http: Http = Http()

    /** 全租户任务的租户清单来源 */
    var tenantSource: TenantSource = TenantSource()

    /** 投递工作线程池 */
    var dispatch: Dispatch = Dispatch()

    /** 执行记录保留期治理 */
    var housekeeping: Housekeeping = Housekeeping()

    /** 执行过程日志回传通道 */
    var logApi: LogApi = LogApi()

    /** HTTP 投递公共约束 */
    class Http {
        /** 建立连接的超时秒数, 作用于全部投递请求 */
        var connectTimeoutSeconds: Long = 5

        /** 响应体读取的字节上限, 超出部分截断丢弃, 防大响应拖垮投递线程 */
        var maxResponseBytes: Int = 65_536

        /** 执行记录 response_snippet 的存储字符数上限 */
        var responseSnippetLength: Int = 2000

        /**
         * 内部目标白名单: host 或 host:port 条目, 命中的目标跳过内网地址拒绝;
         * 开发环境需包含 admin-biz 地址 (如 localhost:7100), 生产按实际内网目标显式配置
         */
        var allowedInternalHosts: MutableList<String> = mutableListOf()
    }

    /** 全租户任务的租户清单来源 */
    class TenantSource {
        /**
         * Admin 启用租户内部契约的基地址 (如 http://localhost:7100), 拼接
         * /internal/upm/tenant/enabled 拉取; 空串表示未配置, 全租户任务触发时整轮跳过并记录错误;
         * 快照缓存 TTL 经 aspen.cache.definitions 声明, 与其他缓存声明同源管理
         */
        var baseUrl: String = ""
    }

    /** 投递工作线程池 */
    class Dispatch {
        /** 核心线程数 */
        var corePoolSize: Int = 4

        /** 最大线程数 */
        var maxPoolSize: Int = 8

        /** 队列容量, 打满后新投递交调用线程执行, 形成自然背压 */
        var queueCapacity: Int = 200
    }

    /** 执行记录保留期治理 */
    class Housekeeping {
        /** 执行记录保留天数, 0 表示关闭按期清理 */
        var logRetentionDays: Int = 30

        /** 保留期清理的系统触发 cron, 显式时区解释 */
        var cron: String = "0 30 3 * * ?"

        /** 系统触发与保留期清理使用的 IANA 时区 */
        var timezoneId: String = "Asia/Shanghai"

        /** 僵尸 RUNNING 判定分钟数: 超过该时长未回写的执行视为实例中断 */
        var staleRunningMinutes: Int = 30
    }

    /**
     * 执行过程日志回传通道 (internal 契约 POST /internal/task/execution-log)
     *
     * 无鉴权内网通道必须有界: 单逻辑执行的回传条数 (跨批次累计) 与管理端读取
     * 均受 [LogApi.maxEntriesPerExecution] 封顶, 超出拒绝, 防止任一内网调用方
     * 无限灌日志造成存储与读取无界 (2026-09-13 审核修复)
     */
    class LogApi {
        /** 回传端点开关 (aspen.task.log-api.enabled), 关闭后 internal 端点退场 */
        var enabled: Boolean = true

        /** 单逻辑执行允许回传的最大日志条数 (跨批次累计), 超出拒绝 */
        var maxEntriesPerExecution: Int = 1000
    }
}
