package com.hoerire.tools.okia.tooling

/**
 * 可用工具注册表。host 注册 descriptor + executor；
 * loop 通过它解析模型工具调用。
 * 契约（issue #127 修正）：registry 是 host 持有并注入的同一对象引用，
 * register / remove 即时生效，活跃回合内允许变更——loop 每段请求前现取
 * snapshot()，新工具对下一轮模型请求可见。Okia.update 仅做配置热更新，
 * 不承担 registry 变更通道。
 * Design source: okia 骨架 ToolRegistry。
 */
interface ToolRegistry {

    // 注册工具
    fun register(descriptor: ToolDescriptor, executor: ToolExecutor): Unit

    // 按名移除
    fun remove(name: String): Unit

    // 按名查找
    fun find(name: String): RegisteredTool?

    // 快照
    fun snapshot(): List<RegisteredTool>
}

/** 绑定 executor 的工具。 */
data class RegisteredTool(
    val descriptor: ToolDescriptor,
    val executor: ToolExecutor
)

data class ToolDescriptor(
    val name: String,
    val description: String,
    val inputSchemaJson: String? = null,
    val kind: ToolKind,
    val wireName: String = ToolWireName.forLocal(name)
)

/** 工具在哪里运行。 */
sealed interface ToolKind {

    /** host 进程内执行。 */
    data object Local : ToolKind

}
