package com.zax.aspen.admin.biz.tenant

import com.zax.aspen.common.core.constant.TenantHttpHeaders
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 租户头解析过滤器
 *
 * 读取 X-Aspen-Tenant-Id 并写入请求级上下文, 供 common-database 的
 * TenantContextSupplier 消费; 仅信任内网调用链 (网关覆盖重写 / 统一任务服务
 * 逐租户投递), 外部流量必须经网关且该头被网关重写; 缺头或格式非法时上下文
 * 保持为空, 租户数据的查询与写入由 fail-closed 过滤器拒绝, 不在本层报错
 */
class TenantHeaderFilter : OncePerRequestFilter() {
    /**
     * 解析租户头并延续过滤链, 请求结束清理上下文
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param filterChain 过滤链
     */
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            request.getHeader(TenantHttpHeaders.TENANT_ID)?.let { raw ->
                val parsed = raw.toLongOrNull()
                if (parsed == null) {
                    log.warn("租户头格式非法, 按缺失处理: {}={}", TenantHttpHeaders.TENANT_ID, raw)
                } else if (parsed > 0) {
                    TenantHeaderContext.set(parsed)
                }
            }
            filterChain.doFilter(request, response)
        } finally {
            TenantHeaderContext.clear()
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TenantHeaderFilter::class.java)
    }
}
