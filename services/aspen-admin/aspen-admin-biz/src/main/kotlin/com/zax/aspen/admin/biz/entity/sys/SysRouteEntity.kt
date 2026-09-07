package com.zax.aspen.admin.biz.entity.sys

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.model.MutableAuditEntity
import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
import org.babyfish.jimmer.sql.Default
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table

/**
 * 保存网关动态路由定义, 是路由分发的唯一权威源
 *
 * 典型场景: 管理端经 SysRouteApi 维护路由, Admin 在事务提交后与启动时把全部启用
 * 路由构建为版本化快照发布到 Redis, Gateway 只读消费; 路由是平台基础设施配置,
 * 全体租户共用, 不做租户隔离; 误删路由凭逻辑删除行恢复, 重新发布后即重新生效
 */
@Entity
@Table(name = "sys_route")
interface SysRouteEntity : MutableAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val routeId: Long

    /** 路由编码, 直接作为 Gateway route id; 小写中划线格式, 全局唯一, 创建后不可修改 */
    val routeCode: String

    /** 路由显示名, 用于管理界面展示与搜索 */
    val routeName: String

    /** 目标地址, 只允许 lb://、http:// 或 https://; 写入前由 Service 校验 */
    val uri: String

    /** 断言列表, 结构 [{name, args}], 至少一条; 复用 api/event 分发契约的 RouteDefinitionPart 保证与发布快照结构一致; Service 写入前校验, 存取经 Jimmer @Serialized 与 JSON 列互转 */
    @Serialized
    val predicates: List<RouteDefinitionPart>

    /** 过滤器列表, 结构 [{name, args}], 可为空列表; 依次叠加生效 */
    @Serialized
    val filters: List<RouteDefinitionPart>

    /** 路由级参数, 如 response-timeout; 只影响网关行为, 不参与业务判断; 无参数时为 null */
    @Serialized
    val metadata: Map<String, String>?

    /** 路由匹配顺序, 数值小者先匹配; 发布快照按本列升序排列 */
    @Default("0")
    val sortOrder: Int

    /** 路由启停状态; disabled 的路由不进入发布快照, 停用即从 Gateway 生效面移除 */
    @Default("ENABLED")
    val status: EnabledStatus
}
