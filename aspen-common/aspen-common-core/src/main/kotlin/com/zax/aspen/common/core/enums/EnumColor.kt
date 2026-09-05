package com.zax.aspen.common.core.enums

/**
 * 枚举与字典项的标签颜色取值约定
 *
 * 前端 Tag 组件按令牌映射到自身组件库的样式, 设计规范调整时不需要后端发版;
 * 语义色表达状态含义, 调色板色用于取值较多的枚举区分展示, 调色板不足时允许十六进制色值
 */
object EnumColor {
    /** 中性灰, 用于停用、归档等无强调语义的状态 */
    const val DEFAULT = "default"

    /** 主题色, 用于进行中、处理中等主流程状态 */
    const val PRIMARY = "primary"

    /** 成功色, 用于启用、已完成、已通过等正向状态 */
    const val SUCCESS = "success"

    /** 警告色, 用于待处理、即将过期等需要关注的状态 */
    const val WARNING = "warning"

    /** 危险色, 用于禁用、失败、已拒绝等负向状态 */
    const val DANGER = "danger"

    /** 调色板红 */
    const val RED = "red"

    /** 调色板火山橙 */
    const val VOLCANO = "volcano"

    /** 调色板橙 */
    const val ORANGE = "orange"

    /** 调色板金 */
    const val GOLD = "gold"

    /** 调色板青柠 */
    const val LIME = "lime"

    /** 调色板绿 */
    const val GREEN = "green"

    /** 调色板青 */
    const val CYAN = "cyan"

    /** 调色板蓝 */
    const val BLUE = "blue"

    /** 调色板极客蓝 */
    const val GEEKBLUE = "geekblue"

    /** 调色板紫 */
    const val PURPLE = "purple"

    /** 调色板品红 */
    const val MAGENTA = "magenta"

    /** 全部合法令牌, 前端据此维护样式映射表 */
    val TOKENS: Set<String> = setOf(
        DEFAULT, PRIMARY, SUCCESS, WARNING, DANGER,
        RED, VOLCANO, ORANGE, GOLD, LIME, GREEN, CYAN, BLUE, GEEKBLUE, PURPLE, MAGENTA,
    )

    /** 十六进制色值格式, 作为调色板不足时的逃生口 */
    private val HEX = Regex("#[0-9a-fA-F]{6}")

    /** 判断颜色是否为合法令牌或十六进制色值 */
    fun isValid(color: String): Boolean = color in TOKENS || HEX.matches(color)
}
