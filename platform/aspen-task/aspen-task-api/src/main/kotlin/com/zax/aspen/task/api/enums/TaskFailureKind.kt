package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 逻辑执行失败类别
 *
 * 失败的可观测分类: 管理端按类别筛选定位 (目标不可达看连接失败, 被安全策略
 * 拦截看目标校验拒绝), 修正参考项目只有异常 message 的缺口; 全租户清单解析
 * 失败发生在展开执行之前, 整轮跳过并记录错误日志, 不产生本类执行记录
 */
@GenDict(code = "task_failure_kind", name = "任务失败类别", group = "task")
enum class TaskFailureKind(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 请求超时: 超过任务配置的 timeout_seconds 仍未取得响应 */
    TIMEOUT("timeout", "请求超时", EnumColor.RED),

    /** HTTP 响应失败: 目标可达但返回非 2xx 状态码 */
    HTTP_ERROR("http_error", "HTTP 响应失败", EnumColor.VOLCANO),

    /** 连接失败: DNS 解析失败、连接被拒或网络不可达 */
    CONNECTION_ERROR("connection_error", "连接失败", EnumColor.MAGENTA),

    /** 目标校验拒绝: SSRF 防护拒绝投递 (协议非法或内网地址不在白名单) */
    TARGET_REJECTED("target_rejected", "目标校验拒绝", EnumColor.DANGER),

    /** 实例中断: 投递实例崩溃后由对账/保留期治理回收的僵尸 RUNNING 执行 */
    INTERRUPTED("interrupted", "实例中断", EnumColor.LIME),
}
