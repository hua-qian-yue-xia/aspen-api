package com.zax.aspen.admin.api.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 强制 admin-api 的目录按《技术架构》7.3 组织: 契约类型目录在前, 业务组目录在后
 *
 * 复合服务 Admin 的 contract/dto/vo 等承载业务组的类型目录下必须是 upm/sys 组目录,
 * 禁止把业务契约直接放在契约类型根, 也禁止以业务组目录打头跳过契约类型层;
 * constant 是无业务组的协议常量目录, 直接承载类型
 */
class ApiPackageStructureTest {
    /** 校验源码树只使用获准的契约类型目录 */
    @Test
    fun `source files live only in sanctioned contract type directories`() {
        val apiRoot = API_ROOT_FILE
        val actualTypeDirs = apiRoot.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()

        assertEquals(EMPTY_SET, actualTypeDirs - SANCTIONED_TYPE_DIRS, "存在未获准的顶层契约类型目录")
    }

    /** 校验承载业务组的类型目录内必须是 upm/sys 组目录 */
    @Test
    fun `group carrying type directories only contain upm or sys groups`() {
        val apiRoot = API_ROOT_FILE

        GROUP_CARRYING_TYPE_DIRS.forEach { typeDir ->
            val groupDirs = File(apiRoot, typeDir).listFiles { file -> file.isDirectory }
                ?.map { it.name }?.toSet() ?: EMPTY_SET
            assertEquals(
                EMPTY_SET,
                groupDirs - BUSINESS_GROUPS,
                "契约类型目录 $typeDir 下存在未获准的业务组目录",
            )
        }
    }

    /** 校验不允许把业务契约文件直接放在契约类型根或 api 根 */
    @Test
    fun `no business contracts at type root or api root`() {
        val apiRoot = API_ROOT_FILE

        val rootKtFiles = apiRoot.listFiles { file -> file.isFile }!!.map { it.name }
        assertEquals(EMPTY_LIST, rootKtFiles, "api 根目录禁止直接放置契约文件: $rootKtFiles")

        // constant 按规范直接承载协议常量文件, 只有承载业务组的类型目录要求先进组目录
        GROUP_CARRYING_TYPE_DIRS.forEach { typeDir ->
            val typeRoot = File(apiRoot, typeDir)
            if (typeRoot.isDirectory) {
                val ktFiles = typeRoot.listFiles { file -> file.isFile }!!.map { it.name }
                assertEquals(EMPTY_LIST, ktFiles, "契约类型目录 $typeDir 根下禁止直接放置契约文件: $ktFiles")
            }
        }
    }

    /** 校验测试源码树同样使用获准的测试目录 */
    @Test
    fun `test sources live only in sanctioned test directories`() {
        val testRoot = File("src/test/kotlin").walkTopDown()
            .filter { it.isDirectory && it.path.endsWith("com/zax/aspen/admin/api") }
            .first()
        val actualTestDirs = testRoot.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()

        assertEquals(EMPTY_SET, actualTestDirs - SANCTIONED_TEST_DIRS, "存在未获准的 api 测试目录")
    }

    private companion object {
        /** §7.3 获准的契约类型目录 */
        val SANCTIONED_TYPE_DIRS = setOf(
            "contract", "dto", "vo", "client", "event", "task", "enums", "error", "validation", "constant",
        )

        /** 承载业务组的契约类型目录 */
        val GROUP_CARRYING_TYPE_DIRS = setOf(
            "contract", "dto", "vo", "client", "event", "task", "enums", "error", "validation",
        )

        /** Admin 复合服务的业务组 */
        val BUSINESS_GROUPS = setOf("upm", "sys")

        /** §7.3 获准的测试目录 */
        val SANCTIONED_TEST_DIRS = setOf("architecture", "contract", "serialization", "validation")

        /** 测试工作目录即模块根, 直接相对定位 main 源码树 */
        val API_ROOT_FILE: File =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isDirectory && it.path.endsWith("com/zax/aspen/admin/api") }
                .first()

        val EMPTY_SET = emptySet<String>()
        val EMPTY_LIST = emptyList<String>()
    }
}
