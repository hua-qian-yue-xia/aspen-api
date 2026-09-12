package com.zax.aspen.task.api.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 强制 task-api 的目录按《技术架构》7.3 组织: 契约类型目录在前, 业务组目录在后
 *
 * Task 是单业务边界服务, 契约类型目录直接承载契约文件, 不设业务组目录;
 * 仍禁止把契约文件直接放在 api 根, 也禁止出现未获准的顶层契约类型目录;
 * constant 是无业务组的协议常量目录, 同样直接承载类型
 */
class ApiPackageStructureTest {
    /** 校验源码树只使用获准的契约类型目录 */
    @Test
    fun `source files live only in sanctioned contract type directories`() {
        val apiRoot = API_ROOT_FILE
        val actualTypeDirs = apiRoot.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()

        assertEquals(EMPTY_SET, actualTypeDirs - SANCTIONED_TYPE_DIRS, "存在未获准的顶层契约类型目录")
    }

    /** 校验单业务边界服务的类型目录内不出现业务组子目录 */
    @Test
    fun `type directories stay flat without group subdirectories`() {
        val apiRoot = API_ROOT_FILE

        SANCTIONED_TYPE_DIRS.forEach { typeDir ->
            val groupDirs = File(apiRoot, typeDir).listFiles { file -> file.isDirectory }
                ?.map { it.name }?.toSet() ?: EMPTY_SET
            assertEquals(EMPTY_SET, groupDirs, "契约类型目录 $typeDir 下不应存在业务组目录")
        }
    }

    /** 校验不允许把业务契约文件直接放在 api 根 */
    @Test
    fun `no business contracts at api root`() {
        val apiRoot = API_ROOT_FILE

        val rootKtFiles = apiRoot.listFiles { file -> file.isFile }!!.map { it.name }
        assertEquals(EMPTY_LIST, rootKtFiles, "api 根目录禁止直接放置契约文件: $rootKtFiles")
    }

    /** 校验测试源码树同样使用获准的测试目录 */
    @Test
    fun `test sources live only in sanctioned test directories`() {
        val testRoot = File("src/test/kotlin").walkTopDown()
            .filter { it.isDirectory && it.path.endsWith("com/zax/aspen/task/api") }
            .first()
        val actualTestDirs = testRoot.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()

        assertEquals(EMPTY_SET, actualTestDirs - SANCTIONED_TEST_DIRS, "存在未获准的 api 测试目录")
    }

    private companion object {
        /** §7.3 获准的契约类型目录 */
        val SANCTIONED_TYPE_DIRS = setOf(
            "contract", "dto", "vo", "client", "event", "task", "enums", "error", "validation", "constant",
        )

        /** §7.3 获准的测试目录 */
        val SANCTIONED_TEST_DIRS = setOf("architecture", "contract", "serialization", "validation")

        /** 测试工作目录即模块根, 直接相对定位 main 源码树 */
        val API_ROOT_FILE: File =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isDirectory && it.path.endsWith("com/zax/aspen/task/api") }
                .first()

        val EMPTY_SET = emptySet<String>()
        val EMPTY_LIST = emptyList<String>()
    }
}
