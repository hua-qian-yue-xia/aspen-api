package com.zax.aspen.common.database.model

import org.babyfish.jimmer.sql.ast.table.Props
import org.babyfish.jimmer.sql.ast.table.PropsFor

/** 声明租户过滤器的挂载类型, Jimmer 会把过滤条件应用到全部继承 TenantScopedEntity 的实体 */
@PropsFor(TenantScopedEntity::class)
abstract class TenantScopedProps : Props
