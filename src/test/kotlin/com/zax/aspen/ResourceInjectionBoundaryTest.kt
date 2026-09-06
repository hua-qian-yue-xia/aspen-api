package com.zax.aspen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 依赖注入边界测试
 *
 * 《技术架构》7.8 第 5 条的强制项: Spring 组件类的协作依赖只能以 @Resource 标注的
 * private lateinit var 字段注入, 禁止构造器注入与 @Autowired; 公共模块 @Bean 工厂
 * 方法装配的基础设施类不是组件扫描类, 不在本测试范围
 */
class ResourceInjectionBoundaryTest {
    /** 组件扫描注解清单, 命中任一即视为受注入规范约束的组件类 */
    private val stereotypeAnnotations =
        listOf("@Service", "@Component", "@Repository", "@RestController", "@Controller")

    /** 校验组件类的主构造不携带依赖参数 */
    @Test
    fun `component classes do not declare constructor dependencies`() {
        val offenders = componentFiles()
            .filter { primaryConstructorParameters(it).isNotBlank() }
            .map { it.path }
            .toList()

        assertEquals(
            emptyList(),
            offenders,
            "以下组件类使用构造器注入, 必须改为 @Resource 标注的 private lateinit var 字段: $offenders",
        )
    }

    /** 校验组件类内的全部 lateinit 属性都以 @Resource 注入 */
    @Test
    fun `lateinit properties in components are resource annotated`() {
        val offenders = componentFiles()
            .filter { file ->
                val lines = file.readLines()
                lines.withIndex().any { (index, line) ->
                    line.contains("lateinit var") && annotationAbove(lines, index) != "@Resource"
                }
            }
            .map { it.path }
            .toList()

        assertEquals(
            emptyList(),
            offenders,
            "以下组件类的 lateinit 属性缺少 @Resource 注解: $offenders",
        )
    }

    /** 校验主源码不出现 @Autowired */
    @Test
    fun `autowired is absent from main sources`() {
        val offenders = mainSourceFiles()
            .filter { it.readText().contains("@Autowired") }
            .map { it.path }
            .toList()

        assertEquals(
            emptyList(),
            offenders,
            "主源码禁止 @Autowired, 统一使用 @Resource 字段注入: $offenders",
        )
    }

    /**
     * 收集受规范约束的组件类源码文件
     *
     * @return 声明了组件扫描注解且不属于 @ConfigurationProperties 绑定类的主源码文件列表
     */
    private fun componentFiles(): List<File> =
        mainSourceFiles()
            .filter { file ->
                val text = file.readText()
                stereotypeAnnotations.any { text.contains(it) } && !text.contains("@ConfigurationProperties")
            }

    /**
     * 收集全部主源码 Kotlin 文件
     *
     * @return 排除构建产物后的 src/main/kotlin 源文件列表
     */
    private fun mainSourceFiles(): List<File> =
        File(".")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("src/main/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .toList()

    /**
     * 提取组件类主构造的参数文本
     *
     * @param file 组件类源码文件, 每个文件只声明一个公开类型
     * @return 主构造括号内的参数文本, 无主构造或主构造无参数时为空字符串
     */
    private fun primaryConstructorParameters(file: File): String {
        val text = file.readText()
        val classMatch =
            Regex("(?:^|\\n)[ \\t]*(?:(?:internal|open|abstract|sealed)[ \\t]+)*class[ \\t]+[A-Za-z_]\\w*[ \\t]*\\(")
                .find(text) ?: return ""
        val openParen = text.indexOf('(', classMatch.range.last)
        var depth = 0
        var index = openParen
        var inString = false
        while (index < text.length) {
            val char = text[index]
            when {
                inString && char == '\\' -> index++
                inString && char == '"' -> inString = false
                !inString && char == '"' -> inString = true
                !inString && char == '(' -> depth++
                !inString && char == ')' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(openParen + 1, index).trim()
                    }
                }
            }
            index++
        }
        return ""
    }

    /**
     * 读取目标行上方最近的非空行
     *
     * @param lines 文件全部行
     * @param index lateinit 属性所在行下标
     * @return 上方最近的非空行内容, 上方无内容时为空字符串
     */
    private fun annotationAbove(lines: List<String>, index: Int): String {
        for (cursor in index - 1 downTo 0) {
            val line = lines[cursor].trim()
            if (line.isNotEmpty()) return line
        }
        return ""
    }
}
