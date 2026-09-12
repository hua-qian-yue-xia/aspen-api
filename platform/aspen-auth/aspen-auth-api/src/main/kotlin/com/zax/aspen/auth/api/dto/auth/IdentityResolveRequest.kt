package com.zax.aspen.auth.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * 第三方身份解析请求
 *
 * Principal SPI resolveByIdentity 的入参: Auth 完成与外部身份提供方的协议交换后,
 * 携带身份标识请用户域查找 (或按策略建号) 本域主体; allowCreate 由登录方式策略
 * 决定——管理端第三方登录固定 false (只认已绑定), app 端可 true (一键登录建号)
 */
data class IdentityResolveRequest(
    /** 外部身份类型, 用户域自解释 (如 wechat/openid、apple/sub), 值域由各用户域定义 */
    @field:NotBlank
    val identityType: String,
    /** 外部身份标识, 如微信 openid/unionid、苹果 sub, 在 (identityType) 内唯一 */
    @field:NotBlank
    val identityId: String,
    /** 身份不存在时是否允许幂等建号: true 时用户域按身份唯一键 find-or-create */
    val allowCreate: Boolean,
    /** 身份提供方返回的展示名等建档资料, 仅 allowCreate 建号时使用, 可为 null */
    val displayName: String?,
)
