package com.zax.aspen.task.api.enums

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 任务投递的 HTTP 方法
 *
 * 任务只支持 HTTP 投递 (内部微服务与外部项目统一目标模型); 方法集合与
 * TaskHttpDispatcher 支持的请求形态一一对应, 请求体仅对 POST/PUT/PATCH 有意义
 */
@GenDict(code = "task_http_method", name = "任务 HTTP 方法", group = "task")
enum class TaskHttpMethod(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** GET 请求, 不携带请求体 */
    GET("get", "GET", EnumColor.DEFAULT),

    /** POST 请求, 通常携带 JSON 请求体 */
    POST("post", "POST", EnumColor.PRIMARY),

    /** PUT 请求, 通常携带完整替换语义的请求体 */
    PUT("put", "PUT", EnumColor.BLUE),

    /** DELETE 请求, 不携带请求体 */
    DELETE("delete", "DELETE", EnumColor.DANGER),

    /** PATCH 请求, 携带局部更新语义的请求体 */
    PATCH("patch", "PATCH", EnumColor.ORANGE),
}
