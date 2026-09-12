package com.zax.aspen.auth.api.enums.auth

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/**
 * 认证客户端 (端) 类型
 *
 * 端是调用方粒度: 管理端 Web、移动应用、设备端与第三方合作方各为一类; 令牌的
 * client_kind claim 携带本枚举 code, 网关据此做 claim 与路径前缀的双向校验
 * (admin 只能打 /admin-api, app 只能打 /app-api, device 只能打 /device-api),
 * 两套用户域 (管理端 UPM 与未来 app 用户域) 靠它保证永不串门
 */
@GenDict(code = "auth_client_kind", name = "认证端类型", group = "auth")
enum class AuthClientKind(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    /** 管理端 Web, 主体来自 Admin UPM, 令牌只能访问 /admin-api 前缀 */
    ADMIN("admin", "管理端", EnumColor.PRIMARY),

    /** 移动应用, 主体来自未来 app 用户域 (C 端), 令牌只能访问 /app-api 前缀 */
    APP("app", "移动应用", EnumColor.SUCCESS),

    /** 设备端, 主体为设备注册表中的设备, 令牌只能访问 /device-api 前缀 */
    DEVICE("device", "设备端", EnumColor.CYAN),

    /** 第三方合作方, 主体为 sys_auth_client 自身 (机器端密钥), 面向服务间开放能力 */
    THIRD_PARTY("third_party", "第三方合作方", EnumColor.PURPLE),
}
