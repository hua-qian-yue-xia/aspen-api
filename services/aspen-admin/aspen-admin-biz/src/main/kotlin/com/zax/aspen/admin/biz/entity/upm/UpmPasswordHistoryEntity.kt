package com.zax.aspen.admin.biz.entity.upm

import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Table

/** 保存历史密码摘要以阻止近期密码复用 */
@Entity
@Table(name = "upm_password_history")
interface UpmPasswordHistoryEntity : UpmCreationEntity {
    @Column(name = "user_id")
    val userId: Long

    /** 只允许保存历史密码摘要, 禁止写入明文 */
    @Column(name = "password_hash")
    val passwordHash: String

    @Column(name = "hash_algorithm")
    val hashAlgorithm: String
}
