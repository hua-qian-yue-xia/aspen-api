package com.zax.aspen.task.biz.dispatch.http

import com.zax.aspen.task.api.enums.TaskHttpMethod
import java.time.Duration

/**
 * 一次 HTTP 投递的完整请求描述
 *
 * 由投递器按任务定义与当次执行租户渲染而成; headers 已并入任务自定义头、
 * 租户头与 Aspen 溯源头; timeout 是本请求的读取超时, 来自任务定义
 */
data class TaskHttpRequest(
    /** 投递使用的 HTTP 方法 */
    val method: TaskHttpMethod,
    /** 已渲染的目标地址 */
    val url: String,
    /** 已渲染并合并全部来源的请求头 */
    val headers: Map<String, String>,
    /** 已渲染的请求体文本, 无请求体时为 null */
    val body: String?,
    /** 本请求的超时时间 */
    val timeout: Duration,
)
