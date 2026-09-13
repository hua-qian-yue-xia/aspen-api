package com.zax.aspen.auth.biz.entity.auth

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Table
import java.time.LocalDateTime

/**
 * 保存登录审计记录, 只追加不修改不删除
 *
 * 典型场景: 每次登录尝试 (无论成败) 与失败原因落一行, 供管理端登录历史查询与
 * 安全排查; 账号不存在与密码错误对外同提示, 行内 failure_code 细分仅供内部;
 * 取代 UPM 已降级废弃的 upm_login_log (权威随多主体域拆分上收 Auth)
 */
@Entity
@Table(name = "auth_login_log")
interface AuthLoginLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val logId: Long

    /** 登录发生的端类型 code (AuthClientKind) */
    val clientKind: String

    /** 登录发生的具体端编码 */
    val clientCode: String

    /** 用户域内主体标识; 凭据未对上时可能为 null */
    val principalId: Long?

    /** 密码登录尝试的账号原文 (失败也记录, 锁定排查用) */
    val account: String?

    /** 认证方式 code (AuthLoginMethodType) */
    val method: String

    /** 登录结果 (AuthLoginResult), 失败按原因分类 */
    val result: String

    /** 细化失败码, 预留 SPI 返回补充 */
    val failureCode: String?

    /** 登录来源 IP (IPv6 长度) */
    val ip: String?

    /** 登录 User-Agent */
    val userAgent: String?

    /** 记录写入时间, 只增不改 */
    val createdAt: LocalDateTime
}
