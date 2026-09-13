package com.zax.aspen.admin.biz.service.sys

import com.zax.aspen.admin.api.dto.sys.SysDictItemSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemStatusUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictItemUpdateDTO
import com.zax.aspen.admin.api.dto.sys.SysDictSaveDTO
import com.zax.aspen.admin.api.dto.sys.SysDictStatusUpdateDTO
import com.zax.aspen.admin.biz.entity.sys.SysDictEntity
import com.zax.aspen.admin.biz.entity.sys.SysDictItemEntity
import com.zax.aspen.admin.biz.repository.sys.SysDictRepository
import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.database.policy.DatabaseLimits
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖字典管理的内置保护与一致性规则
 *
 * 内置字典 (枚举播种生成) 的删除、停用与字典项增删全部拒绝; 字典编码与字典项值
 * 的唯一性、父项归属、默认项唯一「先清后置」与子项存在性保护各自独立覆盖;
 * 替身一律先赋局部变量再进打桩表达式, Mockito 打桩表达式内嵌套创建替身会报
 * UnfinishedStubbing
 */
class SysDictServiceTest {
    private val sysDictRepository: SysDictRepository = Mockito.mock(SysDictRepository::class.java)

    private val service = SysDictService(
        sysDictRepository = sysDictRepository,
        databaseLimits = DatabaseLimits(defaultPageSize = 20, maxPageSize = 100, defaultBatchSize = 100, maxBatchSize = 1000),
    )

    @Test
    fun `create dict rejects duplicated code`() {
        val existing = dict()
        Mockito.`when`(sysDictRepository.findDictByCode("demo")).thenReturn(existing)

        assertFailsWith<IllegalArgumentException> { service.createDict(saveCommand()) }
        Mockito.verify(sysDictRepository, Mockito.never())
            .insertCustomDict(anyValue(), Mockito.anyString())
    }

    @Test
    fun `create dict returns view of inserted dict`() {
        val inserted = dict()
        Mockito.`when`(sysDictRepository.findDictByCode("demo")).thenReturn(null)
        Mockito.`when`(sysDictRepository.insertCustomDict(saveCommand(), "admin:sys-dict")).thenReturn(inserted)

        val view = service.createDict(saveCommand())

        assertEquals("demo", view.dictCode)
        assertEquals(false, view.isBuiltIn)
    }

    @Test
    fun `get dict rejects missing dict`() {
        Mockito.`when`(sysDictRepository.findDictById(404L)).thenReturn(null)

        assertFailsWith<IllegalArgumentException> { service.getDict(404L) }
    }

    @Test
    fun `update dict status rejects disabling built in dict`() {
        val builtIn = dict(isBuiltIn = true)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(builtIn)

        assertFailsWith<IllegalArgumentException> {
            service.updateDictStatus(1L, SysDictStatusUpdateDTO(EnabledStatus.DISABLED))
        }
        Mockito.verify(sysDictRepository, Mockito.never())
            .updateDictStatus(anyValue(), anyValue(), Mockito.anyString())
    }

    @Test
    fun `update dict status allows disabling custom dict`() {
        val existing = dict()
        val disabled = dict()
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(existing, disabled)
        Mockito.`when`(sysDictRepository.updateDictStatus(existing, EnabledStatus.DISABLED, "admin:sys-dict")).thenReturn(disabled)

        val view = service.updateDictStatus(1L, SysDictStatusUpdateDTO(EnabledStatus.DISABLED))

        assertEquals("demo", view.dictCode)
    }

    @Test
    fun `delete dict rejects built in dict`() {
        val builtIn = dict(isBuiltIn = true)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(builtIn)

        assertFailsWith<IllegalArgumentException> { service.deleteDict(1L) }
        Mockito.verify(sysDictRepository, Mockito.never()).deleteDictWithItems(anyValue())
    }

    @Test
    fun `delete dict removes custom dict with items`() {
        val existing = dict()
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(existing)

        service.deleteDict(1L)

        Mockito.verify(sysDictRepository).deleteDictWithItems(existing)
    }

    @Test
    fun `create item rejects built in dict`() {
        val builtIn = dict(isBuiltIn = true)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(builtIn)

        assertFailsWith<IllegalArgumentException> { service.createItem(itemCommand()) }
    }

    @Test
    fun `create item rejects duplicated value`() {
        val target = dict()
        val occupied = item()
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(target)
        Mockito.`when`(sysDictRepository.findItemByValue(1L, "active")).thenReturn(occupied)

        assertFailsWith<IllegalArgumentException> { service.createItem(itemCommand()) }
    }

    @Test
    fun `create item rejects parent from other dict`() {
        val target = dict()
        val foreignParent = item(dictId = 2L)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(target)
        Mockito.`when`(sysDictRepository.findItemByValue(1L, "active")).thenReturn(null)
        Mockito.`when`(sysDictRepository.findItemById(9L)).thenReturn(foreignParent)

        assertFailsWith<IllegalArgumentException> { service.createItem(itemCommand(parentId = 9L)) }
    }

    @Test
    fun `create item clears other defaults before setting default`() {
        val target = dict()
        val inserted = item(isDefault = true)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(target)
        Mockito.`when`(sysDictRepository.findItemByValue(1L, "active")).thenReturn(null)
        Mockito.`when`(sysDictRepository.insertCustomItem(itemCommand(isDefault = true), "admin:sys-dict")).thenReturn(inserted)

        val view = service.createItem(itemCommand(isDefault = true))

        Mockito.verify(sysDictRepository).clearDefaultItems(1L, "admin:sys-dict")
        assertEquals(true, view.isDefault)
    }

    @Test
    fun `update item clears other defaults when flipping default on`() {
        val existing = item(isDefault = false)
        val refreshed = item(isDefault = true)
        Mockito.`when`(sysDictRepository.findItemById(5L)).thenReturn(existing, refreshed)
        Mockito.`when`(sysDictRepository.updateItemDisplay(existing, itemUpdateCommand(isDefault = true), "admin:sys-dict"))
            .thenReturn(refreshed)

        service.updateItem(5L, itemUpdateCommand(isDefault = true))

        Mockito.verify(sysDictRepository).clearDefaultItems(1L, "admin:sys-dict")
    }

    @Test
    fun `update item keeps default untouched when already default`() {
        val existing = item(isDefault = true)
        Mockito.`when`(sysDictRepository.findItemById(5L)).thenReturn(existing, existing)
        Mockito.`when`(sysDictRepository.updateItemDisplay(existing, itemUpdateCommand(isDefault = true), "admin:sys-dict"))
            .thenReturn(existing)

        service.updateItem(5L, itemUpdateCommand(isDefault = true))

        Mockito.verify(sysDictRepository, Mockito.never()).clearDefaultItems(Mockito.anyLong(), Mockito.anyString())
    }

    @Test
    fun `update item status rejects disabling built in dict item`() {
        val existing = item()
        val builtIn = dict(isBuiltIn = true)
        Mockito.`when`(sysDictRepository.findItemById(5L)).thenReturn(existing)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(builtIn)

        assertFailsWith<IllegalArgumentException> {
            service.updateItemStatus(5L, SysDictItemStatusUpdateDTO(EnabledStatus.DISABLED))
        }
    }

    @Test
    fun `delete item rejects built in dict`() {
        val existing = item()
        val builtIn = dict(isBuiltIn = true)
        Mockito.`when`(sysDictRepository.findItemById(5L)).thenReturn(existing)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(builtIn)

        assertFailsWith<IllegalArgumentException> { service.deleteItem(5L) }
    }

    @Test
    fun `delete item rejects when children exist`() {
        val existing = item()
        val target = dict()
        Mockito.`when`(sysDictRepository.findItemById(5L)).thenReturn(existing)
        Mockito.`when`(sysDictRepository.findDictById(1L)).thenReturn(target)
        Mockito.`when`(sysDictRepository.existsChildItem(5L)).thenReturn(true)

        assertFailsWith<IllegalArgumentException> { service.deleteItem(5L) }
        Mockito.verify(sysDictRepository, Mockito.never()).deleteItem(anyValue())
    }

    /**
     * 构造字典新增入参
     *
     * @return 编码为 demo 的固定入参
     */
    private fun saveCommand(): SysDictSaveDTO = SysDictSaveDTO(
        dictCode = "demo",
        dictName = "演示字典",
        dictGroup = "common",
    )

    /**
     * 构造字典项入参
     *
     * @param parentId 父字典项主键 id, 扁平字典为空
     * @param isDefault 是否默认选中
     * @return 值为 active 的固定入参
     */
    private fun itemCommand(parentId: Long? = null, isDefault: Boolean = false): SysDictItemSaveDTO = SysDictItemSaveDTO(
        dictId = 1L,
        parentId = parentId,
        itemLabel = "启用",
        itemValue = "active",
        isDefault = isDefault,
        sortOrder = 1,
    )

    /**
     * 构造字典项展示属性修改入参
     *
     * @param isDefault 是否默认选中
     * @return 标签为启用的固定入参
     */
    private fun itemUpdateCommand(isDefault: Boolean): SysDictItemUpdateDTO = SysDictItemUpdateDTO(
        itemLabel = "启用",
        isDefault = isDefault,
        sortOrder = 1,
    )

    /**
     * 构造字典实体的 Mockito 替身, 须先赋局部变量再进打桩表达式
     *
     * @param dictId 字典主键 id
     * @param dictCode 字典编码
     * @param isBuiltIn 是否内置字典
     * @return 填充服务层与视图映射所需属性的替身
     */
    private fun dict(dictId: Long = 1L, dictCode: String = "demo", isBuiltIn: Boolean = false): SysDictEntity =
        Mockito.mock(SysDictEntity::class.java).apply {
            Mockito.`when`(this.dictId).thenReturn(dictId)
            Mockito.`when`(this.dictCode).thenReturn(dictCode)
            Mockito.`when`(this.dictName).thenReturn("演示字典")
            Mockito.`when`(this.dictGroup).thenReturn("common")
            Mockito.`when`(this.isBuiltIn).thenReturn(isBuiltIn)
            Mockito.`when`(this.status).thenReturn(EnabledStatus.ENABLED)
            Mockito.`when`(this.version).thenReturn(1)
            Mockito.`when`(this.createdAt).thenReturn(LocalDateTime.now())
            Mockito.`when`(this.updatedAt).thenReturn(LocalDateTime.now())
        }

    /**
     * 构造字典项实体的 Mockito 替身, 须先赋局部变量再进打桩表达式
     *
     * @param dictId 所属字典主键 id
     * @param isDefault 是否默认选中
     * @return 填充服务层与视图映射所需属性的替身
     */
    private fun item(dictId: Long = 1L, isDefault: Boolean = false): SysDictItemEntity =
        Mockito.mock(SysDictItemEntity::class.java).apply {
            Mockito.`when`(this.dictItemId).thenReturn(5L)
            Mockito.`when`(this.dictId).thenReturn(dictId)
            Mockito.`when`(this.parentId).thenReturn(null)
            Mockito.`when`(this.itemLabel).thenReturn("启用")
            Mockito.`when`(this.itemValue).thenReturn("active")
            Mockito.`when`(this.isDefault).thenReturn(isDefault)
            Mockito.`when`(this.color).thenReturn(null)
            Mockito.`when`(this.cssClass).thenReturn(null)
            Mockito.`when`(this.sortOrder).thenReturn(1)
            Mockito.`when`(this.status).thenReturn(EnabledStatus.ENABLED)
            Mockito.`when`(this.version).thenReturn(1)
            Mockito.`when`(this.createdAt).thenReturn(LocalDateTime.now())
            Mockito.`when`(this.updatedAt).thenReturn(LocalDateTime.now())
        }

    /**
     * Kotlin 非空参数的 Mockito 匹配器
     *
     * `Mockito.any()` 对 Kotlin 非空参数直接传 null 会 NPE, 经本函数以非空类型
     * 转发后仅登记匹配器, 不产生空值实参
     *
     * @return 未初始化的类型参数占位, 仅用于匹配器登记
     */
    private fun <T> anyValue(): T = Mockito.any()
}
