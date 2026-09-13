package com.zax.aspen.common.web.fixture.controller.admin.route

import com.zax.aspen.common.route.GetRoute
import com.zax.aspen.common.route.OperationTag
import com.zax.aspen.common.route.PostRoute
import com.zax.aspen.common.route.RateLimitSpec

/**
 * 路由套件测试的契约接口
 *
 * 以平台约定的「契约接口声明路由注解 + Controller 零注解实现」风格声明三个端点,
 * 验证注解经 @AliasFor 与接口层解析被 MVC 正确映射; 受众前缀由 Controller 所在的
 * controller/admin 包自动叠加 /admin-api。两个紧凑限流端点 (echo/limited) 各自
 * 服务一个测试方法, 独立 key 互不消耗配额, 保证测试顺序无关
 */
interface FixtureRouteApi {
    /**
     * 限流断言专用端点
     *
     * 60 秒窗口内第 3 次请求被 429 拒绝, 响应携带 code/traceId 扩展字段
     *
     * @return 恒定回显文本
     */
    @PostRoute(
        "/suite/echo",
        summary = "回显样例",
        rateLimit = RateLimitSpec(windowSeconds = 60, limit = 2),
        log = OperationTag.INSERT,
    )
    fun echo(): String

    /**
     * 操作日志断言专用端点
     *
     * 与 echo 相同的紧凑配额但独立计数, 供日志测试完整走「两次放行一次拒绝」序列
     *
     * @return 恒定回显文本
     */
    @PostRoute(
        "/suite/limited",
        summary = "受限样例",
        rateLimit = RateLimitSpec(windowSeconds = 60, limit = 2),
        log = OperationTag.EXPORT,
    )
    fun limited(): String

    /**
     * 文档声明样例端点
     *
     * 只携带 summary, 使用默认限流声明, 验证注解端点的默认装配路径
     *
     * @return 恒定详情文本
     */
    @GetRoute("/suite/detail", summary = "详情样例")
    fun detail(): String
}
