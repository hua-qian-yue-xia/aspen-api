package com.zax.aspen.admin.biz.repository.sys

import com.zax.aspen.admin.api.dto.sys.SysDictItemSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictPageQuery
import com.zax.aspen.admin.api.dto.sys.SysDictSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictUpdateDTO
import com.zax.aspen.admin.biz.entity.sys.SysDictEntity
import com.zax.aspen.admin.biz.entity.sys.SysDictEntityDraft
import com.zax.aspen.admin.biz.entity.sys.SysDictItemEntity
import com.zax.aspen.admin.biz.entity.sys.SysDictItemEntityDraft
import com.zax.aspen.admin.biz.entity.sys.color
import com.zax.aspen.admin.biz.entity.sys.dictCode
import com.zax.aspen.admin.biz.entity.sys.dictGroup
import com.zax.aspen.admin.biz.entity.sys.dictId
import com.zax.aspen.admin.biz.entity.sys.dictItemId
import com.zax.aspen.admin.biz.entity.sys.dictName
import com.zax.aspen.admin.biz.entity.sys.isDefault
import com.zax.aspen.admin.biz.entity.sys.itemLabel
import com.zax.aspen.admin.biz.entity.sys.itemValue
import com.zax.aspen.admin.biz.entity.sys.parentId
import com.zax.aspen.admin.biz.entity.sys.sortOrder
import com.zax.aspen.admin.biz.entity.sys.status
import com.zax.aspen.admin.biz.entity.sys.updatedAt
import com.zax.aspen.admin.biz.entity.sys.updatedBy
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.gen.GenDictDescriptor
import com.zax.aspen.common.core.gen.GenDictItemDescriptor
import com.zax.aspen.common.core.page.PageResult
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.like
import org.babyfish.jimmer.sql.kt.ast.expression.nullValue
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.LocalDateTime

/**
 * 访问全局字典表 sys_dict 与 sys_dict_item
 *
 * SYS 组常驻业务仓储, 服务 @GenDict 播种与未来的字典管理查询; 播种开关只控制
 * common-gen 的目录扫描与启动投递, 不影响本仓储的装配
 */
@Repository
class SysDictRepository(
    private val sqlClient: KSqlClient,
    private val clock: Clock,
) {
    /**
     * 按编码查找未删除字典
     *
     * @param dictCode 字典编码, 小写格式, 全局唯一
     * @return 匹配的字典实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findDictByCode(dictCode: String): SysDictEntity? =
        sqlClient.createQuery(SysDictEntity::class) {
            where(table.dictCode eq dictCode)
            select(table)
        }.fetchOneOrNull()

    /**
     * 查找字典下全部未删除项并按展示顺序排列
     *
     * @param dictId 字典主键 id
     * @return 该字典下的字典项实体列表, 按 sortOrder 升序, 无数据时返回空列表
     */
    fun findItemsByDictId(dictId: Long): List<SysDictItemEntity> =
        sqlClient.createQuery(SysDictItemEntity::class) {
            where(table.dictId eq dictId)
            orderBy(table.sortOrder.asc())
            select(table)
        }.execute()

    /**
     * 新增内置字典, 显式 INSERT_ONLY: 无 id 与业务键的新对象不接受默认 upsert 语义
     *
     * @param descriptor 枚举字典描述, 提供编码、名称与分组
     * @return 落库后的字典实体, 固定为内置、启用状态, version 写 1
     */
    fun insertDict(descriptor: GenDictDescriptor): SysDictEntity =
        sqlClient.entities.save(
            SysDictEntityDraft.`$`.produce {
                dictCode = descriptor.dictCode
                dictName = descriptor.dictName
                dictGroup = descriptor.dictGroup
                isBuiltIn = true
                status = EnabledStatus.ENABLED
                // Jimmer 对未赋值的 @Version 插入写 0, 与「版本从 1 开始」约定和列默认值对齐需显式赋 1
                version = 1
                createdBy = SYSTEM_IDENTITY
                updatedBy = SYSTEM_IDENTITY
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 新增内置字典项, 显式 INSERT_ONLY: 无 id 与业务键的新对象不接受默认 upsert 语义
     *
     * @param dictId 所属字典主键 id
     * @param item 枚举字典项描述, 提供值、标签、颜色与展示顺序
     * @return 落库后的字典项实体, 固定为启用状态, version 写 1
     */
    fun insertItem(dictId: Long, item: GenDictItemDescriptor): SysDictItemEntity =
        sqlClient.entities.save(
            SysDictItemEntityDraft.`$`.produce {
                this.dictId = dictId
                itemValue = item.itemValue
                itemLabel = item.itemLabel
                color = item.color
                sortOrder = item.sortOrder
                status = EnabledStatus.ENABLED
                // Jimmer 对未赋值的 @Version 插入写 0, 与「版本从 1 开始」约定和列默认值对齐需显式赋 1
                version = 1
                createdBy = SYSTEM_IDENTITY
                updatedBy = SYSTEM_IDENTITY
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 把已有字典的名称与分组强制回写为枚举声明值
     *
     * @param existing 已存在的字典实体, 仅取其 dictId 定位行
     * @param descriptor 枚举字典描述, 其名称与分组覆盖既有值
     */
    fun resyncDict(existing: SysDictEntity, descriptor: GenDictDescriptor) {
        sqlClient.createUpdate(SysDictEntity::class) {
            set(table.dictName, descriptor.dictName)
            set(table.dictGroup, descriptor.dictGroup)
            set(table.updatedAt, LocalDateTime.now(clock))
            set(table.updatedBy, SYSTEM_IDENTITY)
            where(table.dictId eq existing.dictId)
        }.execute()
    }

    /**
     * 把已有字典项的展示属性强制回写为枚举声明值
     *
     * @param existing 已存在的字典项实体, 仅取其 dictItemId 定位行
     * @param item 枚举字典项描述, 其标签、颜色与展示顺序覆盖既有值
     */
    fun resyncItem(existing: SysDictItemEntity, item: GenDictItemDescriptor) {
        sqlClient.createUpdate(SysDictItemEntity::class) {
            set(table.itemLabel, item.itemLabel)
            set(table.sortOrder, item.sortOrder)
            if (item.color == null) {
                set(table.color, nullValue())
            } else {
                set(table.color, item.color)
            }
            set(table.updatedAt, LocalDateTime.now(clock))
            set(table.updatedBy, SYSTEM_IDENTITY)
            where(table.dictItemId eq existing.dictItemId)
        }.execute()
    }

    /**
     * 分页查询字典, 编码或名称模糊匹配, 按编码升序保证引用数据的稳定序
     *
     * @param query 分页与过滤条件, 条件全部可空
     * @param pageSize 经 DatabaseLimits 收敛后的单页数量
     * @return 字典实体分页结果
     */
    fun page(query: SysDictPageQuery, pageSize: Int): PageResult<SysDictEntity> {
        val paged = sqlClient.createQuery(SysDictEntity::class) {
            query.keyword?.takeIf { it.isNotBlank() }?.let { keyword ->
                where(or(table.dictCode like "%$keyword%", table.dictName like "%$keyword%"))
            }
            query.dictGroup?.takeIf { it.isNotBlank() }?.let { where(table.dictGroup eq it) }
            query.status?.let { where(table.status eq it) }
            orderBy(table.dictCode.asc())
            select(table)
        }.fetchPage(query.pageNumber - 1, pageSize)
        return PageResult(
            items = paged.rows,
            totalElements = paged.totalRowCount,
            pageNumber = query.pageNumber,
            pageSize = pageSize,
        )
    }

    /**
     * 按主键查找未删除字典
     *
     * @param dictId 字典主键 id
     * @return 匹配的字典实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findDictById(dictId: Long): SysDictEntity? =
        sqlClient.createQuery(SysDictEntity::class) {
            where(table.dictId eq dictId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 新增管理端手工字典, 显式 INSERT_ONLY, 固定非内置与启用状态
     *
     * @param command 字典新增入参, 编码唯一性由调用方预检, 唯一键兜底由数据库保证
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的字典实体
     */
    fun insertCustomDict(command: SysDictSaveDTO, identity: String): SysDictEntity =
        sqlClient.entities.save(
            SysDictEntityDraft.`$`.produce {
                dictCode = command.dictCode
                dictName = command.dictName
                dictGroup = command.dictGroup
                isBuiltIn = false
                status = EnabledStatus.ENABLED
                // Jimmer 对未赋值的 @Version 插入写 0, 与「版本从 1 开始」约定和列默认值对齐需显式赋 1
                version = 1
                createdBy = identity
                updatedBy = identity
            },
        ) {
            setMode(SaveMode.INSERT_ONLY)
        }.modifiedEntity

    /**
     * 按乐观锁更新字典显示属性 (显示名与分组)
     *
     * @param existing 修改前的字典实体, 提供 dictId 与乐观锁 version
     * @param command 字典修改入参
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的字典实体, version 已自增
     */
    fun updateDictDisplay(
        existing: SysDictEntity,
        command: SysDictUpdateDTO,
        identity: String,
    ): SysDictEntity =
        sqlClient.entities.save(
            SysDictEntityDraft.`$`.produce {
                dictId = existing.dictId
                version = existing.version
                dictName = command.dictName
                dictGroup = command.dictGroup
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 按乐观锁更新字典启停状态
     *
     * @param existing 修改前的字典实体, 提供 dictId 与乐观锁 version
     * @param status 目标启停状态
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的字典实体, version 已自增
     */
    fun updateDictStatus(existing: SysDictEntity, status: EnabledStatus, identity: String): SysDictEntity =
        sqlClient.entities.save(
            SysDictEntityDraft.`$`.produce {
                dictId = existing.dictId
                version = existing.version
                this.status = status
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 逻辑删除字典及其全部字典项
     *
     * 字典项先删保证孤儿项不残留, 同事务内执行, 失败整体回滚
     *
     * @param existing 待删除的字典实体, 仅取其 dictId 定位行
     */
    fun deleteDictWithItems(existing: SysDictEntity) {
        findItemsByDictId(existing.dictId).forEach { item ->
            sqlClient.entities.delete(SysDictItemEntity::class, item.dictItemId)
        }
        sqlClient.entities.delete(SysDictEntity::class, existing.dictId)
    }

    /**
     * 按主键查找未删除字典项
     *
     * @param itemId 字典项主键 id
     * @return 匹配的字典项实体, 不存在或已逻辑删除时返回 `null`
     */
    fun findItemById(itemId: Long): SysDictItemEntity? =
        sqlClient.createQuery(SysDictItemEntity::class) {
            where(table.dictItemId eq itemId)
            select(table)
        }.fetchOneOrNull()

    /**
     * 按字典与值查找未删除字典项
     *
     * @param dictId 所属字典主键 id
     * @param itemValue 字典项存储值
     * @return 匹配的字典项实体, 值未被占用时返回 `null`
     */
    fun findItemByValue(dictId: Long, itemValue: String): SysDictItemEntity? =
        sqlClient.createQuery(SysDictItemEntity::class) {
            where(table.dictId eq dictId)
            where(table.itemValue eq itemValue)
            select(table)
        }.fetchOneOrNull()

    /**
     * 判断字典项是否仍有子项
     *
     * 树形字典的删除保护使用; 只取主键列, 子项数量在管理面规模内
     *
     * @param itemId 字典项主键 id
     * @return 存在未删除子项时返回 `true`
     */
    fun existsChildItem(itemId: Long): Boolean =
        sqlClient.createQuery(SysDictItemEntity::class) {
            where(table.parentId eq itemId)
            select(table.dictItemId)
        }.execute().isNotEmpty()

    /**
     * 清空字典下全部默认项标记
     *
     * 经 createUpdate 直写不经草稿拦截器, updatedAt 需手动写入;
     * 与置位新默认项的保存在同一事务内执行
     *
     * @param dictId 所属字典主键 id
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     */
    fun clearDefaultItems(dictId: Long, identity: String) {
        sqlClient.createUpdate(SysDictItemEntity::class) {
            set(table.isDefault, false)
            set(table.updatedAt, LocalDateTime.now(clock))
            set(table.updatedBy, identity)
            where(table.dictId eq dictId, table.isDefault eq true)
        }.execute()
    }

    /**
     * 新增管理端手工字典项, 显式 INSERT_ONLY
     *
     * @param command 字典项新增入参, 值唯一性与父项归属由调用方预检
     * @param identity 操作人身份标识, 写入 createdBy 与 updatedBy 审计列
     * @return 落库后的字典项实体
     */
    fun insertCustomItem(command: SysDictItemSaveDTO, identity: String): SysDictItemEntity =
        sqlClient.entities.save(
            SysDictItemEntityDraft.`$`.produce {
                dictId = command.dictId
                parentId = command.parentId
                itemLabel = command.itemLabel
                itemValue = command.itemValue
                isDefault = command.isDefault
                color = command.color
                cssClass = command.cssClass
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
     * 按乐观锁更新字典项展示属性 (标签、默认标记、颜色、样式类与顺序)
     *
     * 值、所属字典与父项不在更新面内, 由契约入参天然保证不可变
     *
     * @param existing 修改前的字典项实体, 提供 dictItemId 与乐观锁 version
     * @param command 字典项修改入参
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的字典项实体, version 已自增
     */
    fun updateItemDisplay(
        existing: SysDictItemEntity,
        command: SysDictItemUpdateDTO,
        identity: String,
    ): SysDictItemEntity =
        sqlClient.entities.save(
            SysDictItemEntityDraft.`$`.produce {
                dictItemId = existing.dictItemId
                version = existing.version
                itemLabel = command.itemLabel
                isDefault = command.isDefault
                color = command.color
                cssClass = command.cssClass
                sortOrder = command.sortOrder
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 按乐观锁更新字典项启停状态
     *
     * @param existing 修改前的字典项实体, 提供 dictItemId 与乐观锁 version
     * @param status 目标启停状态
     * @param identity 操作人身份标识, 写入 updatedBy 审计列
     * @return 更新落库后的字典项实体, version 已自增
     */
    fun updateItemStatus(existing: SysDictItemEntity, status: EnabledStatus, identity: String): SysDictItemEntity =
        sqlClient.entities.save(
            SysDictItemEntityDraft.`$`.produce {
                dictItemId = existing.dictItemId
                version = existing.version
                this.status = status
                updatedBy = identity
            },
        ).modifiedEntity

    /**
     * 逻辑删除字典项, deleted_at 由 Jimmer 写入当前时间
     *
     * @param existing 待删除的字典项实体, 仅取其 dictItemId 定位行
     */
    fun deleteItem(existing: SysDictItemEntity) {
        sqlClient.entities.delete(SysDictItemEntity::class, existing.dictItemId)
    }

    private companion object {
        /** 播种写入的系统身份, 与操作人审计列的字符串主体约定一致 */
        const val SYSTEM_IDENTITY = "system:gen-dict"
    }
}
