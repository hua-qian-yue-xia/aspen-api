package com.zax.aspen.task.biz.dispatch.http

/**
 * 租户占位符渲染器
 *
 * 把任务定义 URL、请求头值与请求体中的 {tenantId}、{tenantCode}、{tenantName}
 * 占位符替换为当次执行租户的取值; code/name 在租户清单中不可得时回退为租户 id
 * 文本, 保证占位符一定被消除, 目标不会收到裸占位符
 */
object TenantTemplateRenderer {
    /** 租户标识占位符 */
    const val TENANT_ID = "{tenantId}"

    /** 租户编码占位符 */
    const val TENANT_CODE = "{tenantCode}"

    /** 租户名称占位符 */
    const val TENANT_NAME = "{tenantName}"

    /**
     * 渲染模板文本中的全部租户占位符
     *
     * @param template 含占位符的模板文本
     * @param tenantId 租户标识
     * @param tenantCode 租户编码, 未知时传 null, 以租户 id 文本兜底
     * @param tenantName 租户名称, 未知时传 null, 以租户 id 文本兜底
     * @return 占位符全部替换后的文本
     */
    fun render(template: String, tenantId: Long, tenantCode: String?, tenantName: String?): String {
        val idText = tenantId.toString()
        return template
            .replace(TENANT_ID, idText)
            .replace(TENANT_CODE, tenantCode ?: idText)
            .replace(TENANT_NAME, tenantName ?: idText)
    }
}
