package com.zax.aspen.admin.biz.service.sys

import com.zax.aspen.admin.api.event.sys.RouteDefinitionSnapshot
import com.zax.aspen.admin.api.dto.sys.SysRouteSaveDTO
import com.zax.aspen.admin.api.vo.sys.SysRouteVO
import com.zax.aspen.admin.biz.entity.sys.SysRouteEntity
import com.zax.aspen.admin.biz.messaging.redis.sys.SysRouteChangedEvent
import com.zax.aspen.admin.biz.repository.sys.SysRouteRepository
import org.babyfish.jimmer.sql.exception.SaveException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLIntegrityConstraintViolationException

/**
 * 网关路由管理与发布编排
 *
 * SYS 组常驻业务服务: 写入前经快照契约构造完成结构校验 (编码格式、uri 协议、断言
 * 存在性) 与编码唯一、不可变约束, 变更事务提交后触发快照重发布; predicates、filters
 * 与 metadata 经 Jimmer @Serialized 与 JSON 列直接互转, 服务层不做手工 JSON 解析
 */
@Service
class SysRouteService(
    private val sysRouteRepository: SysRouteRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * 查询全部未删除路由的管理视图, 按匹配顺序排列
     *
     * @return 路由管理视图列表, 按 sortOrder 升序, 无数据时返回空列表
     */
    fun listRoutes(): List<SysRouteVO> = sysRouteRepository.findAll().map { it.toView() }

    /**
     * 新增路由; 编码重复拒绝, 成功提交后触发发布
     *
     * @param command 路由新增入参, 编码必须未被占用 (含已删除行保留的编码)
     * @return 已落库的新路由管理视图
     */
    @Transactional
    fun createRoute(command: SysRouteSaveDTO): SysRouteVO {
        validate(command)
        require(sysRouteRepository.findByCode(command.routeCode) == null) {
            "路由编码已存在: ${command.routeCode}"
        }
        val saved = try {
            sysRouteRepository.insert(command, ROUTE_API_IDENTITY)
        } catch (e: Exception) {
            // 已删除行的编码仍被唯一键占用: 编码一经使用即永久保留, 恢复走逻辑删除行恢复
            if (e.isDuplicateKeyViolation()) {
                throw IllegalArgumentException("路由编码已存在或曾删除后保留, 不可复用: ${command.routeCode}", e)
            }
            throw e
        }
        eventPublisher.publishEvent(SysRouteChangedEvent)
        return saved.toView()
    }

    /**
     * 修改路由; 编码不可变, 乐观锁冲突拒绝, 成功提交后触发发布
     *
     * @param routeId 目标路由的主键 id, 不存在时拒绝
     * @param command 路由修改入参, routeCode 必须与既有值一致
     * @return 更新并重查全行后的路由管理视图
     */
    @Transactional
    fun updateRoute(routeId: Long, command: SysRouteSaveDTO): SysRouteVO {
        validate(command)
        val existing = requireNotNull(sysRouteRepository.findById(routeId)) { "路由不存在: $routeId" }
        require(command.routeCode == existing.routeCode) {
            "路由编码创建后不可修改: ${existing.routeCode}"
        }
        try {
            sysRouteRepository.update(existing, command, ROUTE_API_IDENTITY)
        } catch (e: SaveException.OptimisticLockError) {
            throw IllegalArgumentException("路由已被并发修改, 请刷新后重试: $routeId", e)
        }
        eventPublisher.publishEvent(SysRouteChangedEvent)
        // 更新返回的 modifiedEntity 只携带变更列, 管理视图需要重查全行
        val refreshed = requireNotNull(sysRouteRepository.findById(routeId)) { "路由不存在: $routeId" }
        return refreshed.toView()
    }

    /**
     * 逻辑删除路由, 成功提交后触发发布, 路由即从 Gateway 生效面移除
     *
     * @param routeId 目标路由的主键 id, 不存在时拒绝
     */
    @Transactional
    fun deleteRoute(routeId: Long) {
        val existing = requireNotNull(sysRouteRepository.findById(routeId)) { "路由不存在: $routeId" }
        sysRouteRepository.delete(existing)
        eventPublisher.publishEvent(SysRouteChangedEvent)
    }

    /**
     * 经快照契约构造触发结构校验 (编码格式、uri 协议、断言存在性), 并补充显示名校验
     *
     * @param command 待校验的路由新增或修改入参
     * @return 由入参构建的合法路由快照, 仅用于触发构造校验
     */
    private fun validate(command: SysRouteSaveDTO): RouteDefinitionSnapshot {
        require(command.routeName.isNotBlank()) { "路由显示名不得为空" }
        require(command.sortOrder >= 0) { "路由匹配顺序不得为负数: ${command.sortOrder}" }
        return RouteDefinitionSnapshot(
            routeCode = command.routeCode,
            uri = command.uri,
            order = command.sortOrder,
            predicates = command.predicates,
            filters = command.filters,
            metadata = command.metadata,
        )
    }

    /**
     * 行转管理视图, 集合与映射字段已是类型化数据, 无需解析
     *
     * @return 覆盖全部业务列与审计列的路由管理视图
     */
    private fun SysRouteEntity.toView(): SysRouteVO =
        SysRouteVO(
            routeId = routeId,
            routeCode = routeCode,
            routeName = routeName,
            uri = uri,
            sortOrder = sortOrder,
            status = status,
            predicates = predicates,
            filters = filters,
            metadata = metadata ?: emptyMap(),
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * 判断异常链中是否存在唯一键冲突
     *
     * Jimmer 经自身执行器抛出的约束冲突不总是被 Spring 翻译为 DuplicateKeyException,
     * 需要沿 cause 链同时识别 Spring 翻译异常与 JDBC 原生异常
     *
     * @return 异常链中存在 DuplicateKeyException 或 SQLIntegrityConstraintViolationException 时为 `true`
     */
    private fun Exception.isDuplicateKeyViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any {
            it is DuplicateKeyException || it is SQLIntegrityConstraintViolationException
        }

    private companion object {
        /**
         * v1 无鉴权时期管理写入的操作人身份
         *
         * RBAC 就绪后由认证上下文替换为真实主体标识
         */
        const val ROUTE_API_IDENTITY = "internal:route-api"
    }
}
