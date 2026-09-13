package com.zax.aspen.auth.biz.token

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import com.zax.aspen.auth.api.enums.auth.AuthClientKind
import com.zax.aspen.auth.biz.config.AspenAuthProperties
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 覆盖访问令牌的签发、验签与 claims 语义 */
class AuthTokenServiceTest {
    /** 验证签发的令牌可被 JWKS 公钥验签, claims 与 kid 完整 */
    @Test
    fun `issues verifiable token with expected claims`() {
        val service = tokenService()

        val token = service.issueAccessToken(
            principalId = 42L,
            clientKind = AuthClientKind.ADMIN,
            clientCode = "aspen-admin-web",
            tenantId = 7L,
            ttlSeconds = 1800,
        )

        val parsed = SignedJWT.parse(token)
        assertTrue(parsed.verify(RSASSAVerifier(service.publicJwk().toRSAKey())))
        assertEquals("aspen-test-key", parsed.header.keyID)
        assertEquals(JWSAlgorithm.RS256, parsed.header.algorithm)
        val claims = parsed.jwtClaimsSet
        assertEquals("42", claims.subject)
        assertEquals("admin", claims.getClaim("client_kind"))
        assertEquals("aspen-admin-web", claims.getClaim("client_code"))
        assertEquals("7", claims.getClaim("tenant_id"))
        assertEquals("aspen-auth", claims.issuer)
    }

    /** 验证 C 端主体 (无租户) 的令牌省略 tenant_id claim */
    @Test
    fun `omits tenant claim for tenantless principal`() {
        val service = tokenService()

        val token = service.issueAccessToken(
            principalId = 42L,
            clientKind = AuthClientKind.ADMIN,
            clientCode = "aspen-admin-web",
            tenantId = null,
            ttlSeconds = 1800,
        )

        val claims = SignedJWT.parse(token).jwtClaimsSet
        assertTrue(claims.getClaim("tenant_id") == null)
    }

    /** 验证未配置私钥时构造快速失败, 不允许无签发能力的服务实例存在 */
    @Test
    fun `fails fast without private key`() {
        assertFailsWith<IllegalArgumentException> { tokenService(privateKey = "") }
    }

    /**
     * 构造被测服务; 测试密钥对运行期经 nimbus 生成器现场产出, 不落任何字面量
     *
     * @param privateKey 私钥 base64 文本, 缺省现场生成
     * @return 挂接固定时钟的被测服务
     */
    private fun tokenService(privateKey: String = generatePrivateKeyBase64()): AuthTokenService =
        AuthTokenService(
            properties = AspenAuthProperties(
                jwt = AspenAuthProperties.Jwt(privateKey = privateKey, keyId = "aspen-test-key"),
            ),
            clock = Clock.fixed(Instant.parse("2026-09-13T02:00:00Z"), ZoneId.of("UTC")),
        )

    /**
     * 生成 RSA 测试私钥的 PKCS#8 base64 文本
     *
     * @return 满足强度要求的测试私钥编码
     */
    private fun generatePrivateKeyBase64(): String {
        val jwk = RSAKeyGenerator(TEST_KEY_SIZE).keyID("aspen-test-key").generate()
        return Base64.getEncoder().encodeToString(jwk.toPrivateKey().encoded)
    }

    private companion object {
        /** 测试密钥位数, 满足密钥强度门禁 */
        const val TEST_KEY_SIZE = 4096
    }
}
