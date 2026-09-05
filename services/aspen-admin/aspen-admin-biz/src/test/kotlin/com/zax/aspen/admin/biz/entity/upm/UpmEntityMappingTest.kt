package com.zax.aspen.admin.biz.entity.upm

import com.zax.aspen.common.core.enums.common.EnabledStatus
import com.zax.aspen.common.core.enums.common.Gender
import com.zax.aspen.common.core.enums.common.RiskLevel
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Table
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证 UPM Jimmer 模型的表所有权、命名和公共元数据语义 */
class UpmEntityMappingTest {
    /** 验证完整模型包含 24 个 UPM 实体且全部映射到 UPM 表 */
    @Test
    fun `maps all entities to upm tables`() {
        assertEquals(24, entityClasses.size)

        entityClasses.forEach { entityClass ->
            val tableName = assertNotNull(entityClass.getAnnotation(Table::class.java)).name
            assertTrue(tableName.startsWith("upm_"), "$tableName 不属于 UPM")
            assertFalse(tableName.contains("department"), "$tableName 必须使用 dept 命名")
        }
    }

    /** 验证列名与属性名蛇形一致时不声明 Column 注解, 由 Jimmer 自动解析 */
    @Test
    fun `resolves column names without explicit annotations`() {
        entityClasses.forEach { entityClass ->
            val type = ImmutableType.get(entityClass)
            type.props.values.forEach { prop ->
                assertNull(prop.getAnnotation(Column::class.java), "${entityClass.simpleName}.${prop.name} 的列名与属性蛇形一致, 不应声明 @Column")
            }
        }
    }

    /** 验证可更新实体使用乐观锁和 deleted_at 时间戳逻辑删除 */
    @Test
    fun `maps mutable audit metadata`() {
        val userType = ImmutableType.get(UpmUserEntity::class.java)
        val versionProp = userType.getProp("version")
        val deletedAtProp = userType.getProp("deletedAt")

        assertTrue(versionProp.isVersion)
        assertSame(versionProp, userType.versionProp)
        assertEquals("1", assertNotNull(versionProp.getAnnotation(org.babyfish.jimmer.sql.Default::class.java)).value)
        assertTrue(deletedAtProp.isLogicalDeleted)
        assertSame(deletedAtProp, assertNotNull(userType.logicalDeletedInfo).prop)
    }

    /** 验证来源模型的用户业务默认值已声明到 Jimmer 元数据 */
    @Test
    fun `maps source defaults into metadata`() {
        val userType = ImmutableType.get(UpmUserEntity::class.java)
        val permissionType = ImmutableType.get(UpmPermissionEntity::class.java)

        assertEquals("member", userType.getProp("userType").defaultValueRef.value)
        assertEquals("enabled", userType.getProp("status").defaultValueRef.value)
        assertEquals(Gender.UNKNOWN, userType.getProp("gender").defaultValueRef.value)
        assertEquals(0, userType.getProp("failedLoginCount").defaultValueRef.value)
        assertEquals(false, userType.getProp("mustChangePassword").defaultValueRef.value)
        assertEquals(EnabledStatus.ENABLED, permissionType.getProp("status").defaultValueRef.value)
        assertEquals(RiskLevel.NORMAL, permissionType.getProp("riskLevel").defaultValueRef.value)
    }

    /** 验证用户展示字段保持 nickname 和 real_name 设计 */
    @Test
    fun `keeps user naming compatible with the source schema`() {
        val userType = ImmutableType.get(UpmUserEntity::class.java)

        assertTrue("username" in userType.props)
        assertTrue("nickname" in userType.props)
        assertTrue("realName" in userType.props)
        assertFalse("displayName" in userType.props)
        assertEquals("real_name", "realName".toSnakeCase())
        assertEquals("primary_dept_id", "primaryDeptId".toSnakeCase())
    }

    /** 验证 JSON 字段、关系标识和审计快照的代表性属性与迁移列对应 */
    @Test
    fun `maps representative source columns`() {
        val menuType = ImmutableType.get(UpmMenuEntity::class.java)
        val creationType = ImmutableType.get(UpmPasswordHistoryEntity::class.java)

        assertEquals("query_parameters", "queryParameters".toSnakeCase())
        assertEquals("admin", menuType.getProp("platform").defaultValueRef.value)
        assertTrue(creationType.getProp("createdAt").defaultValueRef.value is Supplier<*>)
        assertEquals("scope_dept_id", "scopeDeptId".toSnakeCase())
        assertEquals(
            "before_snapshot",
            "beforeSnapshot".toSnakeCase(),
        )
    }

    /** 验证每个 Jimmer 实体属性蛇形后的列与迁移表字段一一对应 */
    @Test
    fun `keeps entity columns aligned with the migration`() {
        entityClasses.forEach { entityClass ->
            val type = ImmutableType.get(entityClass)
            val tableName = assertNotNull(entityClass.getAnnotation(Table::class.java)).name
            val tableBody = assertNotNull(
                Regex(
                    "CREATE TABLE `$tableName` \\((.*?)\\) ENGINE",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ).find(migrationSql),
                "迁移中缺少 $tableName",
            ).groupValues[1]
            val migrationColumns = Regex("^\\s*`([^`]+)`\\s+", RegexOption.MULTILINE)
                .findAll(tableBody)
                .map { it.groupValues[1] }
                .toSet()
            val entityColumns = type.props.values
                .map { prop -> prop.name.toSnakeCase() }
                .toSet()

            assertEquals(migrationColumns, entityColumns, "$tableName 的 Entity 与迁移字段不一致")
        }
    }

    /** 把属性名按 Jimmer 默认策略转为蛇形列名 */
    private fun String.toSnakeCase(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    /** 从测试类路径读取版本化 UPM 迁移脚本 */
    private val migrationSql: String by lazy {
        val resource = assertNotNull(javaClass.getResourceAsStream(MIGRATION_RESOURCE))
        resource.bufferedReader().use { it.readText() }
    }

    /** UPM 首版全部实体类型, 用于防止表模型静默遗漏 */
    private val entityClasses = listOf(
        UpmTenantEntity::class.java,
        UpmUserEntity::class.java,
        UpmUserCredentialEntity::class.java,
        UpmUserIdentityEntity::class.java,
        UpmUserDeptEntity::class.java,
        UpmDeptEntity::class.java,
        UpmDeptClosureEntity::class.java,
        UpmDeptLeaderEntity::class.java,
        UpmRoleEntity::class.java,
        UpmUserRoleEntity::class.java,
        UpmRoleDeptEntity::class.java,
        UpmRoleInheritanceEntity::class.java,
        UpmMenuEntity::class.java,
        UpmRoleMenuEntity::class.java,
        UpmPermissionEntity::class.java,
        UpmPermissionApiEntity::class.java,
        UpmRolePermissionEntity::class.java,
        UpmMenuPermissionEntity::class.java,
        UpmUserPermissionEntity::class.java,
        UpmUserSessionEntity::class.java,
        UpmUserMfaEntity::class.java,
        UpmPasswordHistoryEntity::class.java,
        UpmLoginLogEntity::class.java,
        UpmAuthorizationChangeLogEntity::class.java,
    )

    /** 保存测试使用的资源常量 */
    private companion object {
        /** UPM 初始 Schema 的全局 Admin 迁移版本 */
        const val MIGRATION_RESOURCE = "/db/migration/upm/V001__create_upm_schema.sql"
    }
}
