package com.zax.aspen.task.biz.dispatch.http

import com.zax.aspen.task.api.enums.TaskFailureKind
import com.zax.aspen.task.biz.config.AspenTaskProperties
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * 任务 HTTP 投递客户端
 *
 * 基于 JDK HttpClient: 每次投递先经 [TaskHttpGuard] 完整校验, 禁跟随重定向
 * (防重定向绕过地址校验), 请求级超时, 响应体按字节上限截断; 连接、超时与协议
 * 异常归类为 TaskFailureKind, 目标 2xx 之外的响应视为 HTTP 响应失败;
 * 客户端实例无状态复用, 全部请求语义由 TaskHttpRequest 携带
 */
class TaskHttpDispatcher(
    private val guard: TaskHttpGuard,
    private val properties: AspenTaskProperties,
) {
    /** 共享 HTTP 客户端: 禁重定向、固定连接超时, 请求级超时由每次请求携带 */
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.of(properties.http.connectTimeoutSeconds, ChronoUnit.SECONDS))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * 执行一次 HTTP 投递并归类结果
     *
     * @param request 已渲染的投递请求
     * @return 投递结果, 任何失败都以 failureKind 分类, 不向调用方抛出业务异常
     */
    fun dispatch(request: TaskHttpRequest): TaskHttpResult {
        val uri = try {
            guard.check(request.url)
        } catch (e: IllegalArgumentException) {
            return TaskHttpResult.failure(TaskFailureKind.TARGET_REJECTED, e.message ?: "目标校验拒绝")
        }
        return try {
            val builder = HttpRequest.newBuilder(uri)
                .timeout(request.timeout)
            request.headers.forEach { (name, value) -> builder.header(name, value) }
            when (request.method) {
                com.zax.aspen.task.api.enums.TaskHttpMethod.GET,
                com.zax.aspen.task.api.enums.TaskHttpMethod.DELETE,
                -> builder.method(request.method.name, HttpRequest.BodyPublishers.noBody())

                else -> builder.method(
                    request.method.name,
                    request.body?.let { HttpRequest.BodyPublishers.ofString(it, StandardCharsets.UTF_8) }
                        ?: HttpRequest.BodyPublishers.noBody(),
                )
            }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            val status = response.statusCode()
            val body = response.body().use { input ->
                input.readNBytes(properties.http.maxResponseBytes).toString(StandardCharsets.UTF_8)
            }
            if (status in 200..299) {
                TaskHttpResult.success(status, body)
            } else {
                TaskHttpResult.failure(TaskFailureKind.HTTP_ERROR, "目标返回非 2xx 状态码: $status", status, body)
            }
        } catch (e: java.net.http.HttpTimeoutException) {
            TaskHttpResult.failure(TaskFailureKind.TIMEOUT, "请求超时: ${e.message}")
        } catch (e: IOException) {
            TaskHttpResult.failure(TaskFailureKind.CONNECTION_ERROR, "连接失败: ${e.message}")
        } catch (e: IllegalArgumentException) {
            TaskHttpResult.failure(TaskFailureKind.TARGET_REJECTED, "请求构造被拒绝: ${e.message}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            TaskHttpResult.failure(TaskFailureKind.CONNECTION_ERROR, "投递线程被中断: ${e.message}")
        }
    }
}
