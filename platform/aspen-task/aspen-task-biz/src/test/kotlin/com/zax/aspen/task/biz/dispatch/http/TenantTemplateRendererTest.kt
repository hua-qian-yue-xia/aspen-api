package com.zax.aspen.task.biz.dispatch.http

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证租户占位符渲染器的替换与兜底行为
 */
class TenantTemplateRendererTest {
    /** 验证 URL、头值与请求体中的全部占位符被替换为租户取值 */
    @Test
    fun `renders all tenant placeholders`() {
        val rendered = TenantTemplateRenderer.render(
            "https://example.com/sync?tenantId={tenantId}&code={tenantCode}&name={tenantName}",
            tenantId = 7L,
            tenantCode = "acme",
            tenantName = "Acme 租户",
        )

        assertEquals("https://example.com/sync?tenantId=7&code=acme&name=Acme 租户", rendered)
    }

    /** 验证编码与名称未知时以租户 id 文本兜底, 不留裸占位符 */
    @Test
    fun `falls back to tenant id when code or name unknown`() {
        val rendered = TenantTemplateRenderer.render(
            "{\"tenant\":\"{tenantCode}\",\"title\":\"{tenantName}\"}",
            tenantId = 9L,
            tenantCode = null,
            tenantName = null,
        )

        assertEquals("{\"tenant\":\"9\",\"title\":\"9\"}", rendered)
    }

    /** 验证无占位符文本原样返回 */
    @Test
    fun `keeps plain text untouched`() {
        assertEquals("https://example.com/health", TenantTemplateRenderer.render("https://example.com/health", 1L, "a", "b"))
    }
}
