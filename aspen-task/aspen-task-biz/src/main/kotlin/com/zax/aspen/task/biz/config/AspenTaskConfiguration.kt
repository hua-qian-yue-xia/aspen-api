package com.zax.aspen.task.biz.config

import com.zax.aspen.task.biz.dispatch.http.TaskHttpDispatcher
import com.zax.aspen.task.biz.dispatch.http.TaskHttpGuard
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 注册 Task 服务运行 Bean
 *
 * 投递线程池与调度线程池分离: Quartz 线程只负责触发与展开执行行, HTTP 投递在
 * 本线程池执行, 慢目标不占用调度吞吐; 缓存声明 (aspen.cache.definitions) 在
 * application.yaml 随服务接线一并声明
 */
@Configuration
@EnableConfigurationProperties(AspenTaskProperties::class)
class AspenTaskConfiguration {
    /**
     * 创建 HTTP 投递目标校验器
     *
     * @param properties Task 运行配置, 提供内部目标白名单
     * @return 目标语法与地址段校验器
     */
    @Bean
    fun taskHttpGuard(properties: AspenTaskProperties): TaskHttpGuard = TaskHttpGuard(properties)

    /**
     * 创建 HTTP 投递客户端
     *
     * @param guard 目标校验器, 每次投递前完整校验
     * @param properties Task 运行配置, 提供连接超时与响应截断上限
     * @return 禁重定向、请求级超时的投递客户端
     */
    @Bean
    fun taskHttpDispatcher(guard: TaskHttpGuard, properties: AspenTaskProperties): TaskHttpDispatcher =
        TaskHttpDispatcher(guard, properties)

    /**
     * 创建逐租户 HTTP 投递的有界工作线程池
     *
     * @param properties Task 运行配置, 提供线程池规模
     * @return 名前缀 task-dispatch- 的线程池, 关闭时等待在途投递最多 30 秒
     */
    @Bean(TASK_DISPATCH_EXECUTOR)
    fun taskDispatchExecutor(properties: AspenTaskProperties): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            // Spring 7 的线程名前缀与容量属性存在只读视图歧义, 统一走显式 setter 保证赋到真实字段
            setThreadNamePrefix("task-dispatch-")
            setCorePoolSize(properties.dispatch.corePoolSize)
            setMaxPoolSize(properties.dispatch.maxPoolSize)
            setQueueCapacity(properties.dispatch.queueCapacity)
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
        }

    companion object {
        /** 投递线程池 Bean 名, 供处理器显式限定注入 */
        const val TASK_DISPATCH_EXECUTOR = "taskDispatchExecutor"
    }
}
