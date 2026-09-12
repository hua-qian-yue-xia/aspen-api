package com.zax.aspen.task.api.contract

import com.zax.aspen.task.api.dto.TaskExecutionLogReportDTO
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 执行过程日志的内部回传契约
 *
 * 属于 internal 契约, 不经网关暴露, 供被投递的内部微服务按步骤上报执行日志
 * (依赖方向表的 business-biz -> aspen-task-api 方向); 目标服务从投递请求的
 * 溯源请求头取得 executionId, 重复上报按 (executionId, seq) 幂等跳过;
 * v1 无鉴权, 依赖「内网可达 + /internal 路径不经网关」兜底
 */
interface TaskLogReportApi {
    /**
     * 批量上报一个逻辑执行的过程日志
     *
     * @param report 上报入参, 同一执行的多条日志一次提交
     */
    @PostMapping(PATH)
    fun reportExecutionLogs(
        @RequestBody @Valid report: TaskExecutionLogReportDTO,
    )

    companion object {
        /** 内部回传端点路径 */
        const val PATH = "/internal/task/execution-log"
    }
}
