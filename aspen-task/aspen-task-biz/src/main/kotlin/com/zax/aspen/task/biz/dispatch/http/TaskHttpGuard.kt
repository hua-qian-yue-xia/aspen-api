package com.zax.aspen.task.biz.dispatch.http

import com.zax.aspen.task.biz.config.AspenTaskProperties
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * HTTP 投递目标校验器 (SSRF 防护)
 *
 * 保存时只做语法校验 (协议与主机存在性, 不产生网络访问); 每次投递前做完整校验:
 * 仅允许 http/https, 解析目标主机全部地址并拒绝环回、私有、链路本地、任意本地、
 * 组播、IPv6 本地唯一与 CGNAT 段, 内部目标必须经 aspen.task.http.allowed-internal-hosts
 * 显式白名单放行; 校验通过返回原 URI 供客户端直用。DNS 解析与真实请求之间存在
 * 理论上的重绑定窗口, v1 接受该窗口并以「校验紧邻请求」缩小暴露面
 */
class TaskHttpGuard(
    properties: AspenTaskProperties,
) {
    /** 经归一化的白名单条目: host (任意端口) 与 host:port 两种形态 */
    private val allowedTargets: Set<String> = properties.http.allowedInternalHosts
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

    /**
     * 校验目标地址语法, 不发起 DNS 解析, 供任务保存时快速失败
     *
     * @param targetUrl 待校验的目标地址
     * @return 解析后的 URI, 调用方可继续使用其组成
     * @throws IllegalArgumentException 协议非 http/https 或缺少主机时抛出
     */
    fun checkSyntax(targetUrl: String): URI {
        val uri = parse(targetUrl)
        requireSchemeAndHost(uri, targetUrl)
        return uri
    }

    /**
     * 投递前完整校验目标地址
     *
     * 命中白名单 (host 或 host:port) 时跳过地址段检查; 其余目标解析全部 InetAddress,
     * 任一地址落入禁止段即拒绝
     *
     * @param targetUrl 待校验的目标地址
     * @return 校验通过的 URI
     * @throws IllegalArgumentException 协议非法、主机缺失、DNS 解析失败或命中禁止地址段时抛出
     */
    fun check(targetUrl: String): URI {
        val uri = parse(targetUrl)
        requireSchemeAndHost(uri, targetUrl)
        val host = uri.host.lowercase(Locale.ROOT)
        if (isAllowlisted(host, uri.port)) {
            return uri
        }
        val addresses = runCatching { InetAddress.getAllByName(host) }
            .getOrElse { throw IllegalArgumentException("目标主机无法解析: $host") }
        addresses.forEach { address ->
            require(!isForbiddenAddress(address)) {
                "目标主机解析到禁止地址: $host -> ${address.hostAddress}"
            }
        }
        return uri
    }

    /**
     * 解析目标地址文本
     *
     * @param targetUrl 目标地址
     * @return 解析后的 URI
     * @throws IllegalArgumentException 地址无法解析时抛出
     */
    private fun parse(targetUrl: String): URI =
        runCatching { URI(targetUrl) }.getOrElse {
            throw IllegalArgumentException("目标地址无法解析: $targetUrl")
        }

    /**
     * 校验协议与主机存在性
     *
     * @param uri 已解析的 URI
     * @param targetUrl 原始目标地址文本, 用于错误提示
     * @throws IllegalArgumentException 协议非 http/https 或主机为空时抛出
     */
    private fun requireSchemeAndHost(uri: URI, targetUrl: String) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "目标协议仅允许 http/https: ${uri.scheme}"
        }
        require(!uri.host.isNullOrBlank()) { "目标地址缺少主机: $targetUrl" }
    }

    /**
     * 判断主机是否命中白名单
     *
     * @param host 小写主机名
     * @param port 请求端口, URI 未显式携带端口时为 -1
     * @return 命中 host 条目或精确 host:port 条目时为 true
     */
    private fun isAllowlisted(host: String, port: Int): Boolean {
        if (host in allowedTargets) {
            return true
        }
        return port != -1 && "$host:$port" in allowedTargets
    }

    /**
     * 判断地址是否落入禁止段
     *
     * @param address 待判断的解析地址
     * @return 环回、私有、链路本地、任意本地、组播、IPv6 本地唯一或 CGNAT 段时为 true
     */
    private fun isForbiddenAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        // IPv6 本地唯一段 fc00::/7: 首字节高 7 位为 1111110
        if (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC) {
            return true
        }
        // CGNAT 段 100.64.0.0/10: 运营商级 NAT 保留段, 同属内网不可达面
        return bytes.size == 4 && bytes[0] == 100.toByte() && bytes[1] in 64..127
    }
}
