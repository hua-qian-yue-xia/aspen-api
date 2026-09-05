package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table
import java.time.Instant

/** 保存用户稳定身份属性、联系方式和登录状态冗余 */
@Entity
@Table(name = "upm_user")
interface UpmUserEntity : UpmMutableEntity {
    @Column(name = "username")
    val username: String

    @Column(name = "nickname")
    val nickname: String?

    @Column(name = "real_name")
    val realName: String?

    @Column(name = "user_type")
    @Default("member")
    val userType: String

    @Column(name = "status")
    @Default("enabled")
    val status: String

    @Column(name = "job_number")
    val jobNumber: String?

    @Column(name = "primary_dept_id")
    val primaryDeptId: Long?

    @Column(name = "position_title")
    val positionTitle: String?

    @Column(name = "avatar_url")
    val avatarUrl: String?

    @Column(name = "email")
    val email: String?

    @Column(name = "email_normalized")
    val emailNormalized: String?

    @Column(name = "email_verified_at")
    val emailVerifiedAt: Instant?

    @Column(name = "mobile_country_code")
    val mobileCountryCode: String?

    @Column(name = "mobile")
    val mobile: String?

    @Column(name = "mobile_normalized")
    val mobileNormalized: String?

    @Column(name = "mobile_verified_at")
    val mobileVerifiedAt: Instant?

    @Column(name = "gender")
    @Default("unknown")
    val gender: String

    @Column(name = "locale")
    val locale: String?

    @Column(name = "timezone")
    val timezone: String?

    @Column(name = "source")
    @Default("local")
    val source: String

    @Column(name = "external_key")
    val externalKey: String?

    @Column(name = "failed_login_count")
    @Default("0")
    val failedLoginCount: Int

    @Column(name = "locked_until")
    val lockedUntil: Instant?

    @Column(name = "last_login_at")
    val lastLoginAt: Instant?

    @Column(name = "last_login_ip")
    val lastLoginIp: String?

    @Column(name = "login_count")
    @Default("0")
    val loginCount: Int

    @Column(name = "authorization_version")
    @Default("1")
    val authorizationVersion: Int

    @Column(name = "password_changed_at")
    val passwordChangedAt: Instant?

    @Column(name = "must_change_password")
    @Default("false")
    val mustChangePassword: Boolean

    @Column(name = "valid_from")
    val validFrom: Instant?

    @Column(name = "valid_to")
    val validTo: Instant?
}
