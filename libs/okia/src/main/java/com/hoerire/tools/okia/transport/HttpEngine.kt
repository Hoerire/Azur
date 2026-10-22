package com.hoerire.tools.okia.transport

interface HttpEngine {

    // 模型流式请求：suspend 到响应头到达后返回。status / headers 先于 body 行
    // 可用，供 loop 做结构化重试决策；返回前可被协程取消，不阻塞线程。
    // 仅此路径触发 beforeRequest / afterRequest hook。
    suspend fun stream(request: HttpRequest): StreamResponse

    suspend fun unary(request: HttpRequest): HttpResponse

    // 释放引擎资源
    fun close(): Unit
}
