package com.zax.aspen.common.web.fixture

import jakarta.validation.constraints.NotBlank
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * 触发服务层方法级校验的样例服务
 *
 * Spring 6.1 起 Controller 参数约束由 MVC 内建校验抛 HandlerMethodValidationException,
 * `ConstraintViolationException` 只来自 `@Validated` 的非 Controller Bean, 本类以
 * 方法级约束复现该路径, 供错误契约测试断言其渲染
 */
@Validated
@Component
class FixtureLookupService {
    /**
     * 按编码查询样例数据
     *
     * @param code 查询编码, 空白值由方法级校验以固定消息拒绝
     * @return 原样返回的编码值
     */
    fun lookup(@NotBlank(message = "查询编码不能为空") code: String): String = code
}
