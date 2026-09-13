package com.zax.aspen.auth.biz.entity.auth

import com.zax.aspen.auth.api.enums.auth.AuthLoginMethodType
import com.zax.aspen.auth.api.enums.auth.CaptchaKind
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table

/**
 * 保存端×登录方式策略, 一行 = 一个端的一种登录方式
 *
 * 典型场景: 认证引擎登录时按端查方式行, 决定用哪条认证主干、挂哪个人机校验闸门、
 * 是否触发首登强制改密与密码有效期; 表值只是选择器, 协议步骤是 Auth 内按方式枚举
 * 实现的代码; (端, 方式) 唯一, 方式行随端级联删除
 */
@Entity
@Table(name = "auth_login_method")
interface AuthLoginMethodEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val loginMethodId: Long

    /** 所属端; 方式行随端级联删除, 端下线时策略一起退场 */
    @ManyToOne
    @JoinColumn(name = "client_id", referencedColumnName = "client_id")
    val client: AuthClientEntity

    /** 所属端主键; client 关联的标量视图, 按端归类查询方式行时使用 */
    @IdView("client")
    val clientId: Long

    /** 登录方式; 认证引擎按本值分发实现, PASSWORD 系走主体+静态凭据主干 */
    val method: AuthLoginMethodType

    /** 验证码闸门, 粒度在端×方式: 密码登录需要人机校验而第三方登录不需要 */
    @Default("NONE")
    val captchaKind: CaptchaKind

    /** 首登是否强制改密; 仅密码系方式有语义, 其余方式恒 false */
    @Default("false")
    val forceChangeOnFirstLogin: Boolean

    /** 密码有效期天数, 过期登录触发强制改密; 仅密码系方式有语义, null 表示不过期 */
    val passwordMaxAgeDays: Int?

    /** 方式专属配置 (如第三方身份提供方别名); 只存引用别名, 密钥本体走环境变量, 存取经 Jimmer @Serialized 与 JSON 列互转 */
    @Serialized
    val config: Map<String, String>?

    /** 登录页展示顺序, 方式行查询按本列升序排列 */
    @Default("0")
    val sortOrder: Int

    /** 方式启停状态; disabled 的方式行即时不可登录 */
    @Default("ENABLED")
    val status: EnabledStatus
}
