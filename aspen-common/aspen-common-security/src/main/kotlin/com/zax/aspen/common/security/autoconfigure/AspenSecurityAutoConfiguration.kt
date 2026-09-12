package com.zax.aspen.common.security.autoconfigure

import com.zax.aspen.common.cache.autoconfigure.AspenCacheAutoConfiguration
import com.zax.aspen.common.cache.support.AspenRedisOperations
import com.zax.aspen.common.database.autoconfigure.AspenDatabaseAutoConfiguration
import com.zax.aspen.common.database.tenant.TenantContextSupplier
import com.zax.aspen.common.security.AspenSecurityProperties
import com.zax.aspen.common.security.consume.ClientConfigSnapshotStore
import com.zax.aspen.common.security.publish.ClientConfigPublisher
import com.zax.aspen.common.security.trust.InternalTrustFilter
import com.zax.aspen.common.security.trust.RequestIdentityContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/**
 * 注册认证配置分发设施与业务进程最小信任链
 *
 * 两部分装配相互独立: 分发部分 (Store/Publisher) 对齐 common-cache 装配模式,
 * after 固定在 AspenCacheAutoConfiguration 之后求值且仅当容器真实产出
 * AspenRedisOperations 时注册, 无 Redis 的服务静默退避; 信任链部分
 * (InternalTrustFilter + TenantContextSupplier) 只依赖 Servlet 能力, 经
 * aspen.security.enabled 开关控制, before 固定在 AspenDatabaseAutoConfiguration
 * 之前注册 TenantContextSupplier, 保证 common-database 的
 * @ConditionalOnBean(TenantContextSupplier) 求值时供给器已就位
 */
@AutoConfiguration(
    before = [AspenDatabaseAutoConfiguration::class],
    after = [AspenCacheAutoConfiguration::class],
)
@ConditionalOnClass(AspenRedisOperations::class)
@EnableConfigurationProperties(AspenSecurityProperties::class)
class AspenSecurityAutoConfiguration {
    /**
     * 装配客户端配置快照加载与持有的消费端设施
     *
     * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
     * @param objectMapper 服务容器的 Jackson 3 mapper
     * @param properties 安全模块配置
     * @return 从 Redis 加载并持有内存快照的存储
     */
    @Bean("aspenClientConfigSnapshotStore")
    @ConditionalOnBean(AspenRedisOperations::class)
    @ConditionalOnMissingBean(ClientConfigSnapshotStore::class)
    fun aspenClientConfigSnapshotStore(
        aspenRedisOperations: AspenRedisOperations,
        objectMapper: ObjectMapper,
        properties: AspenSecurityProperties,
    ): ClientConfigSnapshotStore = ClientConfigSnapshotStore(aspenRedisOperations, objectMapper, properties)

    /**
     * 装配客户端配置信封发布原语
     *
     * 发布时刻优先复用容器既有 Clock (如 common-database 的审计时钟), 容器未声明时
     * 回退部署域默认时区; 本模块不声明兜底 Clock Bean, 避免与 common-database 的
     * @ConditionalOnMissingBean 时钟静默竞争导致时区语义翻转
     *
     * @param aspenRedisOperations common-cache 提供的 Redis 分发原语
     * @param objectMapper 服务容器的 Jackson 3 mapper
     * @param properties 安全模块配置
     * @param clockProvider 容器 Clock 的惰性解析入口, 缺省时回退系统默认时区
     * @return 把配置快照发布到 Redis 介质的发布器
     */
    @Bean("aspenClientConfigPublisher")
    @ConditionalOnBean(AspenRedisOperations::class)
    @ConditionalOnMissingBean(ClientConfigPublisher::class)
    fun aspenClientConfigPublisher(
        aspenRedisOperations: AspenRedisOperations,
        objectMapper: ObjectMapper,
        properties: AspenSecurityProperties,
        clockProvider: ObjectProvider<Clock>,
    ): ClientConfigPublisher = ClientConfigPublisher(
        aspenRedisOperations = aspenRedisOperations,
        objectMapper = objectMapper,
        properties = properties,
        clock = clockProvider.getIfAvailable(Clock::systemDefaultZone),
    )

    /**
     * 注册最小信任链过滤器
     *
     * 注册序低于 Trace ID 过滤器 (最高优先级) 与租户头过滤器 (最高 + 10),
     * 与既有装配序对齐; 信任凭据从配置读入后构造期固化, 运行期不再读取
     *
     * @param properties 安全模块配置
     * @return 信任链过滤器的注册件
     */
    @Bean("aspenInternalTrustFilter")
    @ConditionalOnProperty(prefix = "aspen.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun aspenInternalTrustFilter(properties: AspenSecurityProperties): FilterRegistrationBean<InternalTrustFilter> =
        FilterRegistrationBean(InternalTrustFilter(properties.trustToken)).apply { order = Ordered.HIGHEST_PRECEDENCE + 20 }

    /**
     * 装配请求级租户上下文供给器
     *
     * 取代 Admin 临时的 TenantHeaderFilter 装配: 读取经信任链校验的身份上下文,
     * 缺失时返回 null, common-database 的 fail-closed 租户链随之生效
     *
     * @return 读取 RequestIdentityContext 的租户供给器
     */
    @Bean("aspenTrustTenantContextSupplier")
    @ConditionalOnProperty(prefix = "aspen.security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun aspenTrustTenantContextSupplier(): TenantContextSupplier = TenantContextSupplier {
        RequestIdentityContext.get()?.tenantId
    }
}
