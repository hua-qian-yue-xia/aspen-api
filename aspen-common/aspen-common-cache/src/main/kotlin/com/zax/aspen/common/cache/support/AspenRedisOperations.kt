package com.zax.aspen.common.cache.support

import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.core.StringRedisTemplate
import java.nio.charset.StandardCharsets

/**
 * 非缓存语义的受控 Redis 分发原语
 *
 * 与 AspenCacheOperations 并列的公共访问面: 缓存语义 (确定 Key、显式 TTL) 走
 * AspenCacheOperations; 权威数据分发、单调计数与轻量变更通知等缓存语义不适用的
 * 场景走本原语, 使用场景必须先在《Common 模块设计》登记。Key 与频道由使用方契约
 * 常量统一定义 (跨服务共享时放提供方 api 的 constant 契约), 不经过 CacheKeyBuilder
 * 命名空间; 禁止用本原语绕过缓存 TTL 治理。subscribe 不提供可靠投递, 断线期间的
 * 变更由使用方定义的自愈路径兜底, 可靠业务事件继续使用 RocketMQ
 */
class AspenRedisOperations(
    private val stringRedisTemplate: StringRedisTemplate,
    private val listenerContainer: RedisMessageListenerContainer,
) {
    /**
     * 原子自增并返回新值, 用于单调递增版本号
     *
     * @param key 永久计数器键, 格式由使用方契约常量定义并已通过段格式校验
     * @return 自增后的最新值
     */
    fun increment(key: String): Long {
        requireValidName(key, "Key")
        return execute("increment") {
            requireNotNull(stringRedisTemplate.opsForValue().increment(key)) { "版本计数器取号失败: $key" }
        }
    }

    /**
     * 读取永久键的字符串值, 不校验内容格式
     *
     * @param key 永久键, 格式由使用方契约常量定义并已通过段格式校验
     * @return 键对应的字符串值, 键不存在时返回 `null`
     */
    fun getValue(key: String): String? {
        requireValidName(key, "Key")
        return execute("getValue") { stringRedisTemplate.opsForValue().get(key) }
    }

    /**
     * 无 TTL 整键原子替换, 一条 SET 完成, 不存在删除窗口; 只用于可随时全量重建的分发快照
     *
     * @param key 永久键, 格式由使用方契约常量定义并已通过段格式校验
     * @param value 整键替换写入的字符串内容
     */
    fun setValue(key: String, value: String) {
        requireValidName(key, "Key")
        execute("setValue") { stringRedisTemplate.opsForValue().set(key, value) }
    }

    /**
     * 向频道发布通知消息, 消息体为字符串 (通常为版本号)
     *
     * @param channel 目标频道名, 格式由使用方契约常量定义并已通过段格式校验
     * @param message 通知消息体, 通常为版本号字符串
     */
    fun publish(channel: String, message: String) {
        requireValidName(channel, "频道")
        execute("publish") { stringRedisTemplate.convertAndSend(channel, message) }
    }

    /**
     * 注册频道订阅, 收到消息时以 UTF-8 字符串回调处理器; 容器由公共装配管理生命周期
     *
     * @param channel 目标频道名, 格式由使用方契约常量定义并已通过段格式校验
     * @param handler 消息回调, 入参为 UTF-8 解码后的消息字符串
     */
    fun subscribe(channel: String, handler: (message: String) -> Unit) {
        requireValidName(channel, "频道")
        listenerContainer.addMessageListener(
            MessageListener { message: Message, _: ByteArray? ->
                handler(String(message.body, StandardCharsets.UTF_8))
            },
            ChannelTopic(channel),
        )
    }

    /**
     * 基础设施故障统一包装为 CacheAccessException, 调用方必须显式选择降级策略
     *
     * @param operation 操作名, 用于异常详情定位失败命令
     * @param block 实际执行 Redis 命令的代码块
     * @return 代码块的执行结果
     */
    private fun <T> execute(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: CacheAccessException) {
            throw e
        } catch (e: Exception) {
            throw CacheAccessException("Redis 分发原语执行失败: $operation", e)
        }

    /** 保存分发原语的 Key 与频道命名规则 */
    private companion object {
        /** Key 与频道共用的段格式, 与 CacheKey 段规则一致 */
        val NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")

        /**
         * 校验 Key 或频道名符合分发原语的段格式
         *
         * @param name 待校验的 Key 或频道名
         * @param kind 校验失败提示中使用的名称种类, 如 Key 或频道
         */
        fun requireValidName(name: String, kind: String) {
            require(name.matches(NAME_PATTERN)) { "分发原语的${kind}格式非法: $name" }
        }
    }
}
