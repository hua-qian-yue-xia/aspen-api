package com.zax.aspen.auth.api.contract.auth

import com.zax.aspen.auth.api.dto.auth.IdentityResolveRequest
import com.zax.aspen.auth.api.dto.auth.IdentityResolveResult
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyRequest
import com.zax.aspen.auth.api.dto.auth.PasswordVerifyResult
import com.zax.aspen.auth.api.dto.auth.UserPrincipalDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 认证主体 SPI 的内部契约
 *
 * Auth 访问各端用户域的唯一通道 (技术架构 14.2): 管理端实现位于 Admin UPM
 * (aspen-admin-biz), 未来的 app 用户域与设备注册表各自新增实现并登记到 Auth 的
 * 按端路由; 属 internal 契约, 不经网关暴露, 依赖「内网可达 + 服务身份」兜底;
 * v1 无服务身份时依赖内网可达, 随批次 4 的网关信任链收紧; 破坏性变更升主版本
 */
interface AuthPrincipalApi {
    /**
     * 校验账号密码并返回主体判定
     *
     * 摘要比对、失败计数与锁定窗口全部在用户域完成; NOT_FOUND 与 BAD_CREDENTIALS
     * 的差异只用于用户域内部计数, 调用方必须对外同提示
     *
     * @param request 登录账号与密码明文
     * @return 校验状态与通过时的主体最小视图
     */
    @PostMapping("$PATH/password")
    fun verifyPassword(@RequestBody request: PasswordVerifyRequest): PasswordVerifyResult

    /**
     * 按第三方身份查找或幂等建号
     *
     * 第三方登录 (微信/苹果等) 完成外部协议交换后调用; allowCreate 由登录方式策略
     * 决定, 管理端固定 false, app 端可 true; 建号以 (identityType, identityId)
     * 唯一键幂等, 并发重复请求解析到同一主体
     *
     * @param request 外部身份标识与建号策略
     * @return 解析状态与主体最小视图
     */
    @PostMapping("$PATH/identity")
    fun resolveByIdentity(@RequestBody request: IdentityResolveRequest): IdentityResolveResult

    /**
     * 按主体标识复查主体状态
     *
     * 刷新令牌时调用: 主体被禁用或锁定即拒绝刷新并触发会话吊销; 主体不存在按
     * 禁用语义返回错误
     *
     * @param principalId 用户域内主体标识
     * @return 主体最小视图
     */
    @GetMapping("$PATH/{principalId}")
    fun getPrincipal(@PathVariable("principalId") principalId: Long): UserPrincipalDto

    companion object {
        /** 内部端点路径前缀 */
        const val PATH = "/internal/auth/principal"
    }
}
