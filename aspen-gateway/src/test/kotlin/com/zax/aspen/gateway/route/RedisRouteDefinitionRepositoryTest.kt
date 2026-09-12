package com.zax.aspen.gateway.route

import com.zax.aspen.common.gateway.contract.RouteDefinitionPart
import com.zax.aspen.common.gateway.contract.RouteDefinitionSnapshot
import com.zax.aspen.common.gateway.consume.RouteSnapshotStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖快照到网关定义的映射与只读语义
 */
class RedisRouteDefinitionRepositoryTest {
    private val routeSnapshotStore: RouteSnapshotStore = Mockito.mock(RouteSnapshotStore::class.java)
    private val repository = RedisRouteDefinitionRepository(routeSnapshotStore)

    @Test
    fun `maps snapshots to gateway definitions`() {
        Mockito.`when`(routeSnapshotStore.currentSnapshots()).thenReturn(
            listOf(
                RouteDefinitionSnapshot(
                    routeCode = "aspen-admin",
                    uri = "lb://aspen-admin",
                    order = 3,
                    predicates = listOf(RouteDefinitionPart("Path", mapOf("_genkey_0" to "/admin/**"))),
                    filters = listOf(RouteDefinitionPart("StripPrefix", mapOf("_genkey_0" to "1"))),
                    metadata = mapOf("response-timeout" to "30000"),
                ),
            ),
        )

        val definition = repository.getRouteDefinitions().collectList().block()!!.single()

        assertEquals("aspen-admin", definition.id)
        assertEquals("lb://aspen-admin", definition.uri.toString())
        assertEquals(3, definition.order)
        assertEquals("Path", definition.predicates.single().name)
        assertEquals("/admin/**", definition.predicates.single().args["_genkey_0"])
        assertEquals("StripPrefix", definition.filters.single().name)
        assertEquals("30000", definition.metadata["response-timeout"])
    }

    @Test
    fun `write endpoints are rejected`() {
        assertFailsWith<UnsupportedOperationException> {
            repository.save(reactor.core.publisher.Mono.empty()).block()
        }
        assertFailsWith<UnsupportedOperationException> {
            repository.delete(reactor.core.publisher.Mono.just("aspen-admin")).block()
        }
    }
}
