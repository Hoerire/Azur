package com.hoerire.tools.common.util

import java.lang.reflect.Field


fun collectionElementTypes(
    value: Any?,
    maxCollectionSample: Int
): List<String> {
    return when (value) {
        is Collection<*> -> value.filterNotNull()
            .take(maxCollectionSample)
            .map { it.javaClass.name }
            .distinct()

        is Array<*> -> value.filterNotNull()
            .take(maxCollectionSample)
            .map { it.javaClass.name }
            .distinct()

        else -> emptyList()
    }
}

fun allFieldsOf(clazz: Class<*>): List<Field> =
    xTry("allFieldsOf:${clazz.name}") {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            fields += current.declaredFields
            current = current.superclass
        }
        fields
    } ?: emptyList()

fun superClassesOf(clazz: Class<*>): List<String> = xTry("superClassesOf:${clazz.name}") {
    val chain = mutableListOf<String>()
    var current = clazz.superclass
    while (current != null && current != Any::class.java) {
        chain += current.name
        current = current.superclass
    }
    chain
} ?: emptyList()

fun findField(
    clazz: Class<*>,
    fieldName: String
): Field? = xTry("findField:$fieldName") {
    allFieldsOf(clazz).firstOrNull { it.name == fieldName }
}