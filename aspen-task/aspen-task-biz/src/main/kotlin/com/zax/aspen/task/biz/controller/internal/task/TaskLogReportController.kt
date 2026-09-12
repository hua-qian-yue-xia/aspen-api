package com.zax.aspen.task.biz.controller.internal.task

import com.zax.aspen.task.api.contract.TaskLogReportApi
import com.zax.aspen.task.api.dto.TaskExecutionLogReportDTO
import com.zax.aspen.task.biz.service.task.TaskExecutionLogService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.RestController

/**
 * 执行过程日志的内部回传端点
 *
 * 路径与映射继承 task-api 的 TaskLogReportApi; 属于 internal 契约, 不经网关
 * 暴露, 位于 controller/internal 受众包, 不命中 aspen-common-web 的前缀规则;
 * 与 admin 的 UpmTenantApi 同款「默认开启 + 内网可达」兜底, 供被投递的内部
 * 微服务按步骤上报执行走向
 */
@RestController
@ConditionalOnProperty(prefix = "aspen.task.log-api", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class TaskLogReportController(
    private val taskExecutionLogService: TaskExecutionLogService,
) : TaskLogReportApi {
    override fun reportExecutionLogs(report: TaskExecutionLogReportDTO) {
        taskExecutionLogService.reportExecutionLogs(report)
    }
}
