package com.zax.aspen.admin.biz.service.sys

import com.zax.aspen.admin.api.dto.sys.SysDictItemSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictPageQuery
import com.zax.aspen.admin.api.dto.sys.SysDictSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictUpdateDTO
import com.zax.aspen.admin.api.vo.sys.SysDictItemVO
import com.zax.aspen.admin.api.vo.sys.SysDictVO
import com.zax.aspen.admin.biz.entity.sys.SysDictEntity
import com.zax.aspen.admin.biz.entity.sys.SysDictItemEntity
import com.zax.aspen.admin.biz.repository.sys.SysDictRepository
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.page.PageResult
import com.zax.aspen.common.database.policy.DatabaseLimits
import org.babyfish.jimmer.sql.exception.SaveException
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLIntegrityConstraintViolationException

/**
 * 字典管理与内置保护规则
 *
 * SYS 组常驻业务服务: 承载字典与字典项的管理面读写, 强制数据模型文档 §4/§5 的保护
 * 约束——内置字典 (枚举播种生成) 禁止删除、停用与字典项增删, 防止系统依赖的枚举
 * 翻译丢失; 字典编码与字典项值全局/字典内唯一且被逻辑删除行永久占用; 同字典默认项
 * 唯一经「先清后置」在同一事务内保证; 父项归属与子项存在性维护树一致性
 */
@Service
class SysDictService(
    private val sysDictRepository: SysDictRepository,
    private val databaseLimits: DatabaseLimits,
) {
    /**
     * 分页查询字典
     *
     * @param query 分页与过滤条件, 条件全部可空
     * @return 字典视图分页结果, 按 dictCode 升序
     */
    fun pageDicts(query: SysDictPageQuery): PageResult<SysDictVO> {
        val pageSize = databaseLimits.requirePageSize(query.pageSize)
        val page = sysDictRepository.page(query, pageSize)
        return PageResult(
            items = page.items.map { it.toView() },
            totalElements = page.totalElements,
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
        )
    }

    /**
     * 查询字典详情
     *
     * @param dictId 目标字典的主键 id
     * @return 字典视图, 不存在或已逻辑删除时拒绝
     */
    fun getDict(dictId: Long): SysDictVO = requireDict(dictId).toView()

    /**
     * 新增字典; 编码重复或已被逻辑删除行占用时拒绝
     *
     * @param command 字典新增入参, dictCode 全局唯一且永久占用
     * @return 已落库的字典视图, 手工创建的字典固定为非内置
     */
    @Transactional
    fun createDict(command: SysDictSaveDTO): SysDictVO {
        require(sysDictRepository.findDictByCode(command.dictCode) == null) {
            "字典编码已存在: ${command.dictCode}"
        }
        val saved = try {
            sysDictRepository.insertCustomDict(command, ADMIN_API_IDENTITY)
        } catch (e: Exception) {
            // 已删除行的编码仍被唯一键占用: 编码一经使用即永久保留
            if (e.isDuplicateKeyViolation()) {
                throw IllegalArgumentException("字典编码已存在或曾删除后保留, 不可复用: ${command.dictCode}", e)
            }
            throw e
        }
        return saved.toView()
    }

    /**
     * 修改字典显示属性; 内置字典同样只允许调整显示名与分组
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @param command 字典修改入参, 携带显示名与分组
     * @return 修改并重查全行后的字典视图
     */
    @Transactional
    fun updateDict(dictId: Long, command: SysDictUpdateDTO): SysDictVO {
        val existing = requireDict(dictId)
        try {
            sysDictRepository.updateDictDisplay(existing, command, ADMIN_API_IDENTITY)
        } catch (e: SaveException.OptimisticLockError) {
            throw IllegalArgumentException("字典已被并发修改, 请刷新后重试: $dictId", e)
        }
        return requireDict(dictId).toView()
    }

    /**
     * 修改字典启停状态; 内置字典不允许停用, 防止系统依赖的枚举翻译丢失
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @param command 目标启停状态
     * @return 状态变更并重查全行后的字典视图
     */
    @Transactional
    fun updateDictStatus(dictId: Long, command: SysDictStatusUpdateDTO): SysDictVO {
        val existing = requireDict(dictId)
        require(!(existing.isBuiltIn && command.status == EnabledStatus.DISABLED)) {
            "内置字典不允许停用: ${existing.dictCode}"
        }
        try {
            sysDictRepository.updateDictStatus(existing, command.status, ADMIN_API_IDENTITY)
        } catch (e: SaveException.OptimisticLockError) {
            throw IllegalArgumentException("字典已被并发修改, 请刷新后重试: $dictId", e)
        }
        return requireDict(dictId).toView()
    }

    /**
     * 删除字典及其全部字典项; 内置字典禁止删除
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     */
    @Transactional
    fun deleteDict(dictId: Long) {
        val existing = requireDict(dictId)
        require(!existing.isBuiltIn) { "内置字典禁止删除: ${existing.dictCode}" }
        sysDictRepository.deleteDictWithItems(existing)
    }

    /**
     * 查询指定字典下的全部字典项
     *
     * @param dictId 目标字典的主键 id, 不存在时拒绝
     * @return 字典项视图列表, 按 sortOrder 升序
     */
    fun listItems(dictId: Long): List<SysDictItemVO> {
        requireDict(dictId)
        return sysDictRepository.findItemsByDictId(dictId).map { it.toView() }
    }

    /**
     * 新增字典项; 所属字典为内置字典、值重复或父项不属于同一字典时拒绝
     *
     * @param command 字典项新增入参
     * @return 已落库的字典项视图
     */
    @Transactional
    fun createItem(command: SysDictItemSaveDTO): SysDictItemVO {
        val dict = requireDict(command.dictId)
        require(!dict.isBuiltIn) { "内置字典的字典项由枚举播种维护, 禁止新增: ${dict.dictCode}" }
        require(sysDictRepository.findItemByValue(command.dictId, command.itemValue) == null) {
            "字典项值已存在: ${command.itemValue}"
        }
        command.parentId?.let { parentId ->
            val parent = requireNotNull(sysDictRepository.findItemById(parentId)) {
                "父字典项不存在: $parentId"
            }
            require(parent.dictId == command.dictId) { "父字典项不属于目标字典: $parentId" }
        }
        if (command.isDefault) {
            sysDictRepository.clearDefaultItems(command.dictId, ADMIN_API_IDENTITY)
        }
        val saved = try {
            sysDictRepository.insertCustomItem(command, ADMIN_API_IDENTITY)
        } catch (e: Exception) {
            if (e.isDuplicateKeyViolation()) {
                throw IllegalArgumentException("字典项值已存在或曾删除后保留, 不可复用: ${command.itemValue}", e)
            }
            throw e
        }
        return saved.toView()
    }

    /**
     * 修改字典项展示属性; 值、所属字典与父项不可变更
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     * @param command 字典项修改入参, isDefault 置位时清除同字典其他默认项
     * @return 修改并重查全行后的字典项视图
     */
    @Transactional
    fun updateItem(itemId: Long, command: SysDictItemUpdateDTO): SysDictItemVO {
        val existing = requireItem(itemId)
        if (command.isDefault && !existing.isDefault) {
            sysDictRepository.clearDefaultItems(existing.dictId, ADMIN_API_IDENTITY)
        }
        try {
            sysDictRepository.updateItemDisplay(existing, command, ADMIN_API_IDENTITY)
        } catch (e: SaveException.OptimisticLockError) {
            throw IllegalArgumentException("字典项已被并发修改, 请刷新后重试: $itemId", e)
        }
        return requireNotNull(sysDictRepository.findItemById(itemId)) { "字典项不存在: $itemId" }.toView()
    }

    /**
     * 修改字典项启停状态; 内置字典的字典项不允许停用
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     * @param command 目标启停状态
     * @return 状态变更并重查全行后的字典项视图
     */
    @Transactional
    fun updateItemStatus(itemId: Long, command: SysDictItemStatusUpdateDTO): SysDictItemVO {
        val existing = requireItem(itemId)
        val dict = requireDict(existing.dictId)
        require(!(dict.isBuiltIn && command.status == EnabledStatus.DISABLED)) {
            "内置字典的字典项不允许停用: ${existing.itemValue}"
        }
        try {
            sysDictRepository.updateItemStatus(existing, command.status, ADMIN_API_IDENTITY)
        } catch (e: SaveException.OptimisticLockError) {
            throw IllegalArgumentException("字典项已被并发修改, 请刷新后重试: $itemId", e)
        }
        return requireNotNull(sysDictRepository.findItemById(itemId)) { "字典项不存在: $itemId" }.toView()
    }

    /**
     * 删除字典项; 所属字典为内置字典或仍存在子项时拒绝
     *
     * @param itemId 目标字典项的主键 id, 不存在时拒绝
     */
    @Transactional
    fun deleteItem(itemId: Long) {
        val existing = requireItem(itemId)
        val dict = requireDict(existing.dictId)
        require(!dict.isBuiltIn) { "内置字典的字典项由枚举播种维护, 禁止删除: ${existing.itemValue}" }
        require(!sysDictRepository.existsChildItem(itemId)) { "存在子字典项, 请先删除子项: $itemId" }
        sysDictRepository.deleteItem(existing)
    }

    /**
     * 按主键取字典, 不存在时以校验异常拒绝
     *
     * @param dictId 字典主键 id
     * @return 未删除的字典实体
     */
    private fun requireDict(dictId: Long): SysDictEntity =
        requireNotNull(sysDictRepository.findDictById(dictId)) { "字典不存在: $dictId" }

    /**
     * 按主键取字典项, 不存在时以校验异常拒绝
     *
     * @param itemId 字典项主键 id
     * @return 未删除的字典项实体
     */
    private fun requireItem(itemId: Long): SysDictItemEntity =
        requireNotNull(sysDictRepository.findItemById(itemId)) { "字典项不存在: $itemId" }

    /**
     * 行转字典管理视图
     *
     * @return 覆盖全部业务列与审计列的字典视图
     */
    private fun SysDictEntity.toView(): SysDictVO =
        SysDictVO(
            dictId = dictId,
            dictCode = dictCode,
            dictName = dictName,
            dictGroup = dictGroup,
            isBuiltIn = isBuiltIn,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    /**
     * 行转字典项管理视图
     *
     * @return 覆盖全部业务列与审计列的字典项视图
     */
    private fun SysDictItemEntity.toView(): SysDictItemVO =
        SysDictItemVO(
            dictItemId = dictItemId,
            dictId = dictId,
            parentId = parentId,
            itemLabel = itemLabel,
            itemValue = itemValue,
            isDefault = isDefault,
            color = color,
            cssClass = cssClass,
            sortOrder = sortOrder,
            status = status,
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
        const val ADMIN_API_IDENTITY = "admin:sys-dict"
    }
}
