package com.zax.aspen.task.biz.dispatch.http

import com.zax.aspen.task.biz.config.AspenTaskProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证 HTTP 目标校验器的 SSRF 防护矩阵
 *
 * 覆盖协议白名单、环回/私有/链路本地/任意本地/组播/IPv6 本地唯一/CGNAT 拒绝、
 * 内部目标白名单放行与语法校验不产生网络访问
 */
class TaskHttpGuardTest {
    /** 验证仅允许 http/https 协议且语法校验不做 DNS 解析 */
    @Test
    fun `syntax check allows http and https only without dns`() {
        val guard = guardWithAllowlist()

        assertTrue(guard.checkSyntax("https://example.com/api").host == "example.com")
        assertTrue(guard.checkSyntax("http://example.com:8080/api").port == 8080)
        listOf("ftp://example.com", "file:///etc/passwd", "javascript:alert(1)", "http:///no-host").forEach { url ->
            assertFailsWith<IllegalArgumentException>("应拒绝非 http/https 或缺主机目标: $url") {
                guard.checkSyntax(url)
            }
        }
    }

    /** 验证公网字面地址通过完整校验 */
    @Test
    fun `public literal addresses pass full check`() {
        val guard = guardWithAllowlist()

        assertEquals("8.8.8.8", guard.check("http://8.8.8.8/healthz").host)
        assertEquals("1.1.1.1", guard.check("https://1.1.1.1:443/dns-query").host)
    }

    /** 验证环回、私有、链路本地、任意本地、组播、CGNAT 与 IPv6 本地唯一地址全部被拒绝 */
    @Test
    fun `forbidden address families are rejected`() {
        val guard = guardWithAllowlist()

        listOf(
            "http://127.0.0.1/admin",
            "http://localhost/admin",
            "http://[::1]/admin",
            "http://10.0.0.1/internal",
            "http://172.16.0.1/internal",
            "http://192.168.1.1/internal",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/",
            "http://224.0.0.1/multicast",
            "http://100.64.1.1/cgnat",
            "http://[fd12::1]/unique-local",
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>("应拒绝内网或保留地址目标: $url") {
                guard.check(url)
            }
        }
    }

    /** 验证内部目标白名单按 host 或 host:port 放行, 端口不匹配不放行 */
    @Test
    fun `internal allowlist releases explicit targets only`() {
        val guard = guardWithAllowlist("localhost:7100", "10.20.30.40")

        assertTrue(guard.check("http://localhost:7100/internal/upm/tenant/enabled").port == 7100)
        assertTrue(guard.check("http://10.20.30.40:9200/api").port == 9200)
        assertFailsWith<IllegalArgumentException>("白名单外的同主机端口不应放行") {
            guard.check("http://localhost:9999/internal")
        }
    }

    /**
     * 构造带白名单的校验器实例
     *
     * @param allowlist 内部目标白名单条目
     * @return 待测校验器
     */
    private fun guardWithAllowlist(vararg allowlist: String): TaskHttpGuard {
        val properties = AspenTaskProperties()
        properties.http.allowedInternalHosts.addAll(allowlist)
        return TaskHttpGuard(properties)
    }
}
