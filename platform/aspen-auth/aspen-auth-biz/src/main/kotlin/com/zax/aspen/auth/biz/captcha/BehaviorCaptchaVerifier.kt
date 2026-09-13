package com.zax.aspen.auth.biz.captcha

import com.zax.aspen.auth.biz.config.AspenAuthProperties
import com.zax.aspen.common.core.error.BusinessException
import com.zax.aspen.common.core.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import java.io.IOException

/**
 * 三方行为验证码闸门 (CaptchaKind.SLIDER)
 *
 * 服务端二次校验: 把前端票据提交给服务商校验端点, 只有服务商明确 success 才
 * 放行; 端点或凭据未配置时 fail-closed (依赖不可用), 不允许无凭据放行;
 * 服务商选型 (极验/腾讯等) 落地时按其协议调整请求体组装, 本类是对外的稳定边界
 */
@Component
class BehaviorCaptchaVerifier(
    private val properties: AspenAuthProperties,
    private val restClient: RestClient,
) : CaptchaVerifier {
    /**
     * 向服务商提交票据做服务端二次校验
     *
     * @param captchaToken 前端票据, 缺失直接拒绝
     * @throws BusinessException 票据缺失、闸门未接线、依赖不可用或校验不通过时拒绝
     */
    override fun verify(captchaToken: String?) {
        if (captchaToken.isNullOrBlank()) {
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, "请先完成人机验证")
        }
        val captcha = properties.captcha
        if (captcha.validateUrl.isBlank() || captcha.credentialId.isBlank() || captcha.credentialSecret.isBlank()) {
            log.error("行为验证码闸门未配置服务商凭据, fail-closed 拒绝登录")
            throw BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "行为验证码服务未接线")
        }
        val body = mapOf(
            "captchaId" to captcha.credentialId,
            "captchaSecret" to captcha.credentialSecret,
            "captchaToken" to captchaToken,
        )
        val response = try {
            restClient.post()
                .uri(captcha.validateUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
        } catch (e: IOException) {
            throw BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "行为验证码服务暂时不可用", e)
        } catch (e: org.springframework.web.client.RestClientException) {
            throw BusinessException(CommonErrorCode.DEPENDENCY_UNAVAILABLE, "行为验证码服务暂时不可用", e)
        }
        val success = response?.get("success")?.asString() ?: "false"
        if (success != "true") {
            throw BusinessException(CommonErrorCode.UNAUTHORIZED, "人机验证未通过, 请重试")
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(BehaviorCaptchaVerifier::class.java)
    }
}
