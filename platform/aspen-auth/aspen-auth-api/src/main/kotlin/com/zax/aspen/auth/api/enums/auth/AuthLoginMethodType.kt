package com.zax.aspen.auth.api.enums.auth

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 登录方式类型
 *
 * sys_auth_login_method 的 method 列取值; 认证引擎按方式枚举分发实现, 前三类是
 * 「主体 + 静态凭据」的密码登录泛化 (设备密钥与合作方密钥与用户密码同构), 后两类
 * 是第三方身份登录 (Auth 编排与外部身份提供方的协议交换, 绑定关系存各用户域);
 * SMS_OTP 与小程序登录暂不准入, 出现真实需求走枚举准入流程
 */
@GenDict(code = "auth_login_method", name = "登录方式", group = "auth")
enum class AuthLoginMethodType(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 用户账号密码登录, 凭据归各用户域 (管理端为 upm_user_credential), 失败计数与锁定在用户域判定 */
    PASSWORD("password", "账号密码", EnumColor.PRIMARY),

    /** 设备密钥登录, 主体为设备注册表中的设备, 与密码登录同一引擎主干 (预留, 待设备域立项) */
    DEVICE_SECRET("device_secret", "设备密钥", EnumColor.CYAN),

    /** 合作方客户端密钥登录, 主体为 sys_auth_client 自身, 验 secret_hash (预留, 待开放能力立项) */
    CLIENT_SECRET("client_secret", "客户端密钥", EnumColor.PURPLE),

    /** 微信第三方登录, 管理端扫一扫仅认已绑定账号, app 端按方式策略可 find-or-create (预留) */
    THIRD_PARTY_WECHAT("third_party_wechat", "微信登录", EnumColor.SUCCESS),

    /** 苹果登录, 客户端传 identity_token, Auth 验 Apple JWKS 取 sub 作为身份 ID (预留, 待 app 域立项) */
    THIRD_PARTY_APPLE("third_party_apple", "苹果登录", EnumColor.GEEKBLUE),
}
