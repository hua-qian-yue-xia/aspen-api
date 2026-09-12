package com.zax.aspen.admin.biz.repository.sys

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
import com.zax.aspen.admin.biz.entity.sys.itemLabel
import com.zax.aspen.admin.biz.entity.sys.itemValue
import com.zax.aspen.admin.biz.entity.sys.sortOrder
import com.zax.aspen.admin.biz.entity.sys.updatedAt
import com.zax.aspen.admin.biz.entity.sys.updatedBy
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.gen.GenDictDescriptor
import com.zax.aspen.common.core.gen.GenDictItemDescriptor
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.nullValue
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

    private companion object {
        /** 播种写入的系统身份, 与操作人审计列的字符串主体约定一致 */
        const val SYSTEM_IDENTITY = "system:gen-dict"
    }
}
