package com.zax.aspen.auth.biz.token

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.biz.config.AspenAuthProperties
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * 访问令牌 (JWT) 签发与 JWKS 发布
 *
 * RS256 非对称签名: 私钥只在本服务持有 (PKCS#8 base64 经环境变量注入), 网关
 * 凭 JWKS 公钥验签, 私钥不落配置中心与仓库 (技术架构 14.2 密钥边界); claims
 * 携带 sub/client_kind/client_code/tenant_id, 网关据此做 claim 与路径前缀的
 * 双向校验并重注内部身份头; 未配置私钥时启动即快速失败, 不允许无签发能力的
 * 认证服务上线
 */
@Service
class AuthTokenService(
    properties: AspenAuthProperties,
    private val clock: Clock,
) {
    /** RS256 签名器, 构造期由私钥固化 */
    private val signer: RSASSASigner

    /** 密钥标识, 令牌头 kid 与 JWKS 对齐 */
    private val keyId: String

    /** 签发方标识, 写入 iss claim */
    private val issuer: String

    /** 发布给网关的公钥 JWK (只含公开参数), 构造期由私钥重建并固化 */
    private val cachedPublicJwk: RSAKey

    init {
        val rawKey = properties.jwt.privateKey.trim()
        require(rawKey.isNotEmpty()) {
            "ASPEN_AUTH_JWT_PRIVATE_KEY 未配置, 拒绝启动认证服务 (PKCS#8 base64)"
        }
        keyId = properties.jwt.keyId
        issuer = properties.jwt.issuer
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(rawKey))) as java.security.interfaces.RSAPrivateCrtKey
        signer = RSASSASigner(privateKey)
        // PKCS#8 RSA 私钥携带 CRT 参数 (模数 + 公钥指数), 公钥可由私钥确定性重建, 无需第二把钥匙
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(java.security.spec.RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
        cachedPublicJwk = RSAKey.Builder(publicKey)
            .keyID(keyId)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build()
    }

    /**
     * 发布给网关的公钥 JWK
     *
     * @return 只含公开参数的 RSA JWK
     */
    fun publicJwk(): RSAKey = cachedPublicJwk

    /**
     * 签发访问令牌
     *
     * @param principalId 用户域内主体标识, 写入 sub claim
     * @param clientKind 端类型, 写入 client_kind claim (网关前缀校验依据)
     * @param clientCode 具体端编码, 写入 client_code claim
     * @param tenantId 主体租户标识, 写入 tenant_id claim; C 端主体为 null 时省略
     * @param ttlSeconds 令牌有效期 (秒), 来自端覆盖或全局默认
     * @return 已签名的 JWT 紧凑串
     */
    fun issueAccessToken(
        principalId: Long,
        clientKind: AuthClientKind,
        clientCode: String,
        tenantId: Long?,
        ttlSeconds: Long,
    ): String {
        val now = Instant.now(clock)
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build()
        val claimsBuilder = com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(principalId.toString())
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttlSeconds, ChronoUnit.SECONDS)))
            .jwtID(UUID.randomUUID().toString())
            .claim(CLAIM_CLIENT_KIND, clientKind.code)
            .claim(CLAIM_CLIENT_CODE, clientCode)
        if (tenantId != null) {
            claimsBuilder.claim(CLAIM_TENANT_ID, tenantId.toString())
        }
        val token = com.nimbusds.jwt.SignedJWT(header, claimsBuilder.build())
        token.sign(signer)
        return token.serialize()
    }

    private companion object {
        /** 令牌公共 claim 名: 端类型 */
        const val CLAIM_CLIENT_KIND = "client_kind"

        /** 令牌公共 claim 名: 具体端编码 */
        const val CLAIM_CLIENT_CODE = "client_code"

        /** 令牌公共 claim 名: 租户标识 (字符串形态, 避免大整数精度差异) */
        const val CLAIM_TENANT_ID = "tenant_id"
    }
}
