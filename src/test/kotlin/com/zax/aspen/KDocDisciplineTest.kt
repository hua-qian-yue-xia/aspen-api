package com.zax.aspen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KDoc 纪律测试
 *
 * 《技术架构》7.8 第 12 条的强制项: 方法必须使用完整块 KDoc, 概述段之后逐一标注
 * 每个值参数的 @param 与非 Unit 返回的 @return; 禁止只有单行概述的方法注释;
 * override 方法与契约一致时省略 KDoc。扫描全部模块的 main 与 test 源码集,
 * 反引号命名的测试方法天然被解析器跳过
 */
class KDocDisciplineTest {
    /** 校验全部源码的方法 KDoc 满足完整块格式 */
    @Test
    fun `every non override function documents params and return`() {
        val violations = sourceFiles()
            .flatMap(::checkFile)
            .sorted()

        assertEquals(emptyList(), violations, "方法 KDoc 不符合完整块格式 (概述段 + 空行 + @param/@return):\n${violations.joinToString("\n")}")
    }

    /**
     * 单文件检查, 提取全部函数声明的 KDoc 违规
     *
     * @param file 待检查的 Kotlin 源文件
     * @return 「文件:行号 函数名 缺失项」违规列表
     */
    private fun checkFile(file: File): List<String> {
        val lines = file.readLines()
        val violations = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (
                !line.contains("fun ") ||
                line.contains("fun interface") ||
                line.trimStart().startsWith("*") ||
                line.trimStart().startsWith("//")
            ) {
                index++
                continue
            }
            val declarationLine = index
            val signatureParts = collectSignature(lines, index)
            if (signatureParts == null) {
                index++
                continue
            }
            val (signature, endLine) = signatureParts
            index = endLine + 1
            val funDeclaration = FunDeclaration.parse(signature, precedingKDoc(lines, declarationLine)) ?: continue
            val missing = funDeclaration.missingTags()
            if (missing.isNotEmpty()) {
                violations.add("${file.path}:${declarationLine + 1} ${funDeclaration.name} -> ${missing.joinToString()}")
            }
        }
        return violations
    }

    /**
     * 从声明行起收集到参数括号闭合的完整签名文本
     *
     * @param lines 文件全部行
     * @param start 函数声明所在行下标
     * @return 拼接后的签名文本与签名结束行下标, 12 行内未闭合时返回 `null` 跳过
     */
    private fun collectSignature(lines: List<String>, start: Int): Pair<String, Int>? {
        var depth = 0
        var opened = false
        val builder = StringBuilder()
        for (i in start until minOf(lines.size, start + 12)) {
            builder.append(lines[i]).append(' ')
            for (char in lines[i]) {
                when (char) {
                    '(' -> {
                        depth++
                        opened = true
                    }
                    ')' -> depth--
                }
            }
            if (opened && depth == 0) {
                return builder.toString() to i
            }
        }
        return null
    }

    /**
     * 提取紧邻声明之前的 KDoc 块, 中间允许空行、单行注解与跨行注解参数
     *
     * @param lines 文件全部行
     * @param declarationLine 函数声明所在行下标
     * @return 声明前的完整 KDoc 文本, 不存在时返回 `null`
     */
    private fun precedingKDoc(lines: List<String>, declarationLine: Int): String? {
        var cursor = declarationLine - 1
        var annotationDepth = 0
        var insideAnnotationArgs = false
        while (cursor >= 0) {
            val trimmed = lines[cursor].trim()
            when {
                annotationDepth > 0 -> {
                    trimmed.forEach { char ->
                        when (char) {
                            '(' -> annotationDepth++
                            ')' -> annotationDepth--
                        }
                    }
                    cursor--
                }
                insideAnnotationArgs -> {
                    // 自下而上穿越跨行注解: 参数行直接跳过, 遇 @ 开头行即到注解开头
                    if (trimmed.startsWith("@")) {
                        insideAnnotationArgs = false
                    }
                    cursor--
                }
                trimmed.isEmpty() -> cursor--
                trimmed.startsWith("@") -> {
                    var lineDepth = 0
                    trimmed.forEach { char ->
                        when (char) {
                            '(' -> lineDepth++
                            ')' -> lineDepth--
                        }
                    }
                    if (lineDepth > 0) {
                        annotationDepth = lineDepth
                    }
                    cursor--
                }
                trimmed.startsWith(")") -> {
                    insideAnnotationArgs = true
                    cursor--
                }
                trimmed.endsWith("*/") -> {
                    val block = StringBuilder()
                    var blockCursor = cursor
                    while (blockCursor >= 0 && !lines[blockCursor].contains("/**")) {
                        block.insert(0, lines[blockCursor] + "\n")
                        blockCursor--
                    }
                    if (blockCursor < 0) {
                        return null
                    }
                    block.insert(0, lines[blockCursor] + "\n")
                    return block.toString()
                }
                else -> return null
            }
        }
        return null
    }

    /** 单个函数声明的解析结果与 KDoc 校验 */
    private data class FunDeclaration(
        val name: String,
        val isOverride: Boolean,
        val parameterNames: List<String>,
        val requiresReturn: Boolean,
        val kDoc: String?,
    ) {
        /** 计算缺失的 @param 与 @return 标签 */
        fun missingTags(): List<String> {
            val missing = mutableListOf<String>()
            if (isOverride || name == "main") {
                return missing
            }
            if (kDoc == null) {
                return listOf("缺少 KDoc 块")
            }
            parameterNames.forEach { parameter ->
                if (!Regex("@param\\s+[`\\[]?$parameter\\b").containsMatchIn(kDoc)) {
                    missing.add("@param $parameter")
                }
            }
            if (requiresReturn && !Regex("@return\\b").containsMatchIn(kDoc)) {
                missing.add("@return")
            }
            return missing
        }

        companion object {
            /**
             * 从签名文本与其前置 KDoc 解析声明信息
             *
             * @param signature collectSignature 拼接的签名文本
             * @param kDoc precedingKDoc 提取的 KDoc 文本, 可为 `null`
             * @return 解析结果, 无法可靠解析时返回 `null` 由调用方跳过
             */
            fun parse(signature: String, kDoc: String?): FunDeclaration? {
                val nameMatch = Regex("fun\\s+(?:<[^>]*>\\s+)?(?:[A-Za-z_][\\w.<>]*\\.)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*[({<]").find(signature)
                    ?: return null
                val name = nameMatch.groupValues[1]
                val parameterZone = signature.substringAfter('(', missingDelimiterValue = "").substringBefore(')')
                val parameterNames = parameterZone.splitTopLevel(',')
                    .map { parameter ->
                        Regex("(?:vararg\\s+|noinline\\s+|crossinline\\s+)?(?:private\\s+)?(?:val\\s+|var\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s*:").find(parameter)?.groupValues?.get(1)
                    }
                    .filterNotNull()
                val returnTypeMatch = Regex("\\)\\s*:\\s*([^={\\s]+)").find(signature.substringAfter(nameMatch.value))
                val requiresReturn = returnTypeMatch != null && returnTypeMatch.groupValues[1].substringBefore('<') != "Unit"
                return FunDeclaration(
                    name = name,
                    isOverride = signature.contains(" override "),
                    parameterNames = parameterNames,
                    requiresReturn = requiresReturn,
                    kDoc = kDoc,
                )
            }

            /**
             * 按顶层逗号切分, 忽略嵌套泛型与括号内的逗号
             *
             * @param separator 顶层切分分隔符
             * @return 切分后的参数文本列表, 空白段落丢弃
             */
            private fun String.splitTopLevel(separator: Char): List<String> {
                val parts = mutableListOf<String>()
                val current = StringBuilder()
                var angleDepth = 0
                var parenDepth = 0
                for (char in this) {
                    when {
                        char == '<' -> angleDepth++
                        char == '>' -> angleDepth--
                        char == '(' -> parenDepth++
                        char == ')' -> parenDepth--
                        char == separator && angleDepth == 0 && parenDepth == 0 -> {
                            parts.add(current.toString())
                            current.setLength(0)
                        }
                        else -> current.append(char)
                    }
                }
                if (current.isNotBlank()) {
                    parts.add(current.toString())
                }
                return parts
            }
        }
    }

    /**
     * 收集全部模块的 main 与 test 源码文件, 排除构建产物
     *
     * @return 仓库内全部 Kotlin 源文件列表
     */
    private fun sourceFiles(): List<File> =
        File(".")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("src/main/kotlin/") || it.path.contains("src/test/kotlin/") }
            .filter { !it.path.contains("/build/") }
            .toList()
}
