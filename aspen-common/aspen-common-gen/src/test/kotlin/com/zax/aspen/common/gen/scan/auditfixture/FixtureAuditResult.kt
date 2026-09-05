package com.zax.aspen.common.gen.scan.auditfixture

import com.zax.aspen.common.core.enums.AspenEnum
import com.zax.aspen.common.core.enums.EnumColor
import com.zax.aspen.common.core.gen.GenDict

/** 与订单状态不同包的已声明枚举, 用于验证多包扫描合并 */
@GenDict(code = "fixture_audit_result", name = "审计结果", group = "audit")
enum class FixtureAuditResult(
    override val code: String,
    override val description: String,
    override val color: String?,
) : AspenEnum {
    PASS("pass", "通过", EnumColor.SUCCESS),
    REJECT("reject", "驳回", EnumColor.WARNING),
}
