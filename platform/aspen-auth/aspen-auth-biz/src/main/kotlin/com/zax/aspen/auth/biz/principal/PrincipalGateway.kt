package com.zax.aspen.auth.biz.principal

import com.zax.aspen.auth.api.dto.auth.IdentityResolveRequest
import com.zax.aspen.auth.api.dto.auth.IdentityResolveResult
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyResult
import com.zax.aspen.auth.api.dto.auth.UserPrincipalDto
import com.zax.aspen.auth.biz.config.AspenAuthProperties
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.IOException

/**
 * 主体 SPI 的用户域网关: 登录链路访问 Admin UPM 的 HTTP 客户端
 *
 * 技术架构 14.2: Auth 只能经 AuthPrincipalApi 契约访问用户域, 不直连表;
 * v1 目标固定为 Admin (管理端用户域), app 用户域与设备注册表立项后按端路由
 * 扩展本网关; 调用失败统一翻译为依赖不可用, 登录链路快速失败
 */
@Component
class PrincipalGateway(
    private val properties: AspenAuthProperties,
    private val restClient: RestClient,
) {
    /**
     * 委托用户域校验账号密码
     *
     * @param request 登录账号与密码明文
     * @return 用户域的校验判定
     * @throws BusinessException 用户域端点不可达或响应损坏时按依赖不可用拒绝
     */
    fun verifyPassword(request: PasswordVerifyRequest): PasswordVerifyResult =
        post("$PRINCIPAL_PASSWORD_PATH", request, PasswordVerifyResult::class.java)

    /**
     * 委托用户域按第三方身份查找主体
     *
     * @param request 外部身份标识与建号策略
     * @return 用户域的解析判定
     * @throws BusinessException 用户域端点不可达或响应损坏时按依赖不可用拒绝
     */
    fun resolveByIdentity(request: IdentityResolveRequest): IdentityResolveResult =
        post("$PRINCIPAL_IDENTITY_PATH", request, IdentityResolveResult::class.java)

    /**
     * 委托用户域复查主体状态
     *
     * @param principalId 用户域内主体标识
     * @return 主体最小视图
     * @throws BusinessException 用户域端点不可达或响应损坏时按依赖不可用拒绝
     */
    fun getPrincipal(principalId: Long): UserPrincipalDto =
        get("$PRINCIPAL_PATH/$principalId", UserPrincipalDto::class.java)

    /**
     * 发起 POST 调用并解析响应体
     *
     * @param path 相对路径, 拼接在 base-url 之后
     * @param body 请求体对象
     * @param type 响应体目标类型
     * @return 解析后的响应对象
     * @throws BusinessException 传输失败或响应体缺失时按依赖不可用拒绝
     */
    private fun <T : Any> post(path: String, body: Any, type: Class<T>): T =
        call(type) {
            restClient.post()
                .uri("${properties.principal.baseUrl}$path")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(type)
        }

    /**
     * 发起 GET 调用并解析响应体
     *
     * @param path 相对路径, 拼接在 base-url 之后
     * @param type 响应体目标类型
     * @return 解析后的响应对象
     * @throws BusinessException 传输失败或响应体缺失时按依赖不可用拒绝
     */
    private fun <T : Any> get(path: String, type: Class<T>): T =
        call(type) {
            restClient.get()
                .uri("${properties.principal.baseUrl}$path")
                .retrieve()
                .body(type)
        }

    /**
     * 执行调用并统一翻译故障
     *
     * @param type 响应体目标类型, 用于空响应的错误定位
     * @param exchange 发起调用并返回响应体的惰性代码块
     * @return 解析后的响应对象
     * @throws BusinessException 传输失败或响应体缺失时按依赖不可用拒绝
     */
    private fun <T : Any> call(type: Class<T>, exchange: () -> T?): T =
        try {
            exchange() ?: throw IllegalArgumentException("用户域响应体缺失: ${type.simpleName}")
        } catch (e: IOException) {
            throw dependencyUnavailable(e)
        } catch (e: org.springframework.web.client.RestClientException) {
            throw dependencyUnavailable(e)
        } catch (e: IllegalArgumentException) {
            throw dependencyUnavailable(e)
        }

    /**
     * 构造依赖不可用异常并记录原因
     *
     * @param cause 原始传输或解析故障
     * @return 统一的依赖不可用业务异常
     */
    private fun dependencyUnavailable(cause: Exception): BusinessException {
        log.warn("用户域主体 SPI 调用失败, base-url={}", properties.principal.baseUrl, cause)
        return BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "用户身份服务暂时不可用", cause)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(PrincipalGateway::class.java)

        /** 主体 SPI 契约路径 (AuthPrincipalApi.PATH 的镜像常量, 避免跨层引用控制器注解) */
        const val PRINCIPAL_PATH = "/internal/auth/principal"

        /** 密码校验端点相对路径 */
        const val PRINCIPAL_PASSWORD_PATH = "$PRINCIPAL_PATH/password"

        /** 第三方身份解析端点相对路径 */
        const val PRINCIPAL_IDENTITY_PATH = "$PRINCIPAL_PATH/identity"
    }
}
