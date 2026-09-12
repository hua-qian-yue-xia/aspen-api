package com.zax.aspen.task.biz.dispatch.http

import com.zax.aspen.task.api.enums.TaskFailureKind

/**
 * 一次 HTTP 投递的结果
 *
 * success 为 true 当且仅当目标返回 2xx; failureKind 为 null 表示成功或
 * 未分类的内部错误 (errorMessage 说明), httpStatus 在取得响应时非空
 */
data class TaskHttpResult(
    /** 是否取得 2xx 响应 */
    val success: Boolean,
    /** 目标返回的 HTTP 状态码, 未取得响应 (超时/连接失败/校验拒绝) 时为 null */
    val httpStatus: Int?,
    /** 目标响应体文本, 已按字节上限截断 */
    val body: String?,
    /** 失败类别, 成功时为 null */
    val failureKind: TaskFailureKind?,
    /** 失败原因文本 */
    val errorMessage: String?,
) {
    companion object {
        /**
         * 构造成功结果
         *
         * @param httpStatus 2xx 状态码
         * @param body 响应体文本
         * @return 成功的投递结果
         */
        fun success(httpStatus: Int, body: String): TaskHttpResult =
            TaskHttpResult(success = true, httpStatus = httpStatus, body = body, failureKind = null, errorMessage = null)

        /**
         * 构造失败结果
         *
         * @param failureKind 失败类别
         * @param errorMessage 失败原因文本
         * @param httpStatus 目标返回的状态码, 未取得响应时为 null
         * @param body 目标响应体文本, 未取得时为 null
         * @return 失败的投递结果
         */
        fun failure(
            failureKind: TaskFailureKind,
            errorMessage: String,
            httpStatus: Int? = null,
            body: String? = null,
        ): TaskHttpResult =
            TaskHttpResult(success = false, httpStatus = httpStatus, body = body, failureKind = failureKind, errorMessage = errorMessage)
    }
}
