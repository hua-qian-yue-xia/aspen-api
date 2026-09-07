package com.zax.aspen.admin.biz.repository.sys

import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntityDraft
import com.zax.aspen.admin.biz.entity.sys.routeCode
import com.zax.aspen.admin.biz.entity.sys.routeId
import com.zax.aspen.admin.biz.entity.sys.status
import com.zax.aspen.admin.biz.entity.sys.sortOrder
import com.zax.aspen.common.core.enums.common.EnabledStatus
import jakarta.annotation.Resource
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository

/**
 * 访问网关路由表 sys_route
 *
 * SYS 组常驻业务仓储, 服务路由管理与发布快照构建; 逻辑删除由 Jimmer 按 deleted_at
 * 自动过滤, 查询无需手工排除已删除行
 */
@Repository
class SysRouteRepository {
    @Resource
    private lateinit var sqlClient: KSqlClient

    /**
     * 查询全部未删除路由, 按匹配顺序排列, 供管理端列表使用
     *
     * @return 未删除路由实体列表, 按 sortOrder 升序, 无数据时返回空列表
     */
    fun findAll(): List<SysRouteEntity> =
        sqlClient.createQuery(SysRouteEntity::class) {
            orderBy(table.sortOrder.asc())
            select(table)
        }.execute()

    /**
     * 查询全部启用路由, 按匹配顺序排列, 供发布快照构建使用
     *
     * @return 启用状态的路由实体列表, 按 sortOrder 升序, 停用或已删除的路由不在其中
     */
    fun findAllEnabled(): List<SysRouteEntity> =
        sqlClient.createQuery(SysRouteEntity::class) {
            where(table.status eq EnabledStatus.ENABLED)
            orderBy(table.sortOrder.asc())
            select(table)
        }.execute()

    /**
     * 按编码查找未删除路由
     *
     * @param routeCode 路由编码, 小写中划线格式, 全局唯一
     * @return 匹配的路由实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findByCode(routeCode: String): SysRouteEntity? =
        sqlClient.createQuery(SysRouteEntity::class) {
            where(table.routeCode eq routeCode)
            select(table)
        }.fetchOneOrNull()

    /**
     * 按主键查找未删除路由
     *
     * @param routeId 路由表主键 id
     * @return 匹配的路由实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findById(routeId: Long): SysRouteEntity? =
        sqlClient.createQuery(SysRouteEntity::class) {
            where(table.routeId eq routeId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 新增路由, 显式 INSERT_ONLY: 无 id 与业务键的新对象不接受默认 upsert 语义
     *
     * @param command 路由新增入参, 全部业务列取自该对象, version 固定写 1
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的路由实体, 含数据库生成的 id
     */
    fun insert(command: SysRouteSaveDTO, identity: String): SysRouteEntity =
        sqlClient.entities.save(
            SysRouteEntityDraft.`$`.produce {
                routeCode = command.routeCode
                routeName = command.routeName
                uri = command.uri
                predicates = command.predicates
                filters = command.filters
                metadata = command.metadata.ifEmpty { null }
                sortOrder = command.sortOrder
                status = command.status
                // Jimmer 对未赋值的 @Version 插入写 0, 与「版本从 1 开始」约定和列默认值对齐需显式赋 1
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 按乐观锁更新路由业务列
     *
     * 携带既有 version 触发 Jimmer 乐观锁校验, 并发冲突抛出 OptimisticLockError;
     * updated_at 由审计拦截器写入, 此处只负责操作人
     *
     * @param existing 修改前的路由实体, 提供 routeId 与乐观锁 version
     * @param command 路由修改入参, 全部业务列以该对象覆盖
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的路由实体, version 已自增
     */
    fun update(existing: SysRouteEntity, command: SysRouteSaveDTO, identity: String): SysRouteEntity =
        sqlClient.entities.save(
            SysRouteEntityDraft.`$`.produce {
                routeId = existing.routeId
                version = existing.version
                routeName = command.routeName
                uri = command.uri
                predicates = command.predicates
                filters = command.filters
                metadata = command.metadata.ifEmpty { null }
                sortOrder = command.sortOrder
                status = command.status
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 逻辑删除路由, deleted_at 由 Jimmer 写入当前时间, 删除人审计留空
     *
     * @param existing 待删除的路由实体, 仅取其 routeId 定位行
     */
    fun delete(existing: SysRouteEntity) {
        sqlClient.entities.delete(SysRouteEntity::class, existing.routeId)
    }
}
