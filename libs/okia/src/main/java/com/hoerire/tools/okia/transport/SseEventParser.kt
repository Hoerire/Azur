package com.hoerire.tools.okia.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SseEventParser {

    /**
     * 聚合事件流。状态在 flow 构建器内创建，冷流：每次 collect 独立
     * 状态。上游异常 / 取消直接传播。
     */
    fun parse(lines: Flow<SseLine>): Flow<SseEvent> = flow {
        val dataBuffer = StringBuilder()
        var eventType: String? = null

        suspend fun dispatch() {
            if (dataBuffer.isNotEmpty()) {
                emit(SseEvent(data = dataBuffer.toString(), event = eventType))
            }
            dataBuffer.clear()
            eventType = null
        }

        lines.collect { line ->
            when {
                line.data == null -> Unit                        // 注释行 / keep-alive
                line.data.isEmpty() -> dispatch()                // 空行 = 事件边界
                else -> parseField(line.data, dataBuffer) { eventType = it }
            }
        }
        dispatch()  // EOF flush：无尾空行的挂起事件
    }

    // 单行字段解析：第一个冒号分隔字段名 / 值；值以一个前导空格开头时移除该空格
    private fun parseField(
        line: String,
        dataBuffer: StringBuilder,
        setEventType: (String?) -> Unit
    ) {
        val colon = line.indexOf(':')
        if (colon < 0) return  // 无冒号：字段名为空，忽略该行
        val name = line.substring(0, colon)
        val value = when {
            colon + 1 >= line.length -> ""
            line[colon + 1] == ' ' -> line.substring(colon + 2)
            else -> line.substring(colon + 1)
        }
        when (name) {
            "data" -> {
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(value)
            }

            "event" -> setEventType(value)
            else -> Unit  // id / retry / 未知字段忽略
        }
    }
}
