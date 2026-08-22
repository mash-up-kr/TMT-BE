package com.tmt.input.http.config

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertTrue

/**
 * springdoc은 스키마 이름을 클래스의 simple name으로 잡는다. 이름이 같으면 뒤에 읽힌 정의가 앞을
 * 덮어써서 **에러도 경고도 없이 스펙에서 필드가 사라진다.** `TicketSummary`(TMT-174)와
 * `PlaceSummary`·`Photo`(TMT-183)가 그렇게 나갔다.
 *
 * 담는 값이 같으면 하나로 합쳐지는 것이 맞으므로, 이름이 같은데 **모양이 다른** 경우만 잡는다.
 */
class SchemaNameCollisionTest {
    @Test
    fun `이름이 같은 DTO는 모양도 같아야 한다`() {
        val byName =
            dataClassesInMain()
                .mapNotNull { kClass ->
                    val shape = kClass.primaryConstructor?.parameters?.mapNotNull { it.name } ?: return@mapNotNull null
                    Triple(kClass.java.simpleName, kClass.java.name, shape)
                }.groupBy { it.first }

        val collisions =
            byName.filterValues { entries -> entries.map { it.third }.distinct().size > 1 }

        val report =
            collisions.entries.joinToString("\n") { (name, entries) ->
                "$name\n" + entries.joinToString("\n") { "    ${it.second} ${it.third}" }
            }

        assertTrue(collisions.isEmpty(), "simple name이 같은데 모양이 다른 DTO가 있다 — 스펙에서 한쪽이 덮인다\n$report")
    }

    private fun dataClassesInMain(): List<kotlin.reflect.KClass<*>> {
        val root = mainClassesRoot()
        val loader = javaClass.classLoader

        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map {
                it
                    .relativeTo(root)
                    .path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
            }.filter { it.startsWith(PACKAGE) }
            .mapNotNull { runCatching { Class.forName(it, false, loader).kotlin }.getOrNull() }
            .filter { runCatching { it.isData }.getOrDefault(false) }
            .toList()
    }

    /** 테스트가 아니라 main 산출물만 훑는다. */
    private fun mainClassesRoot(): File =
        File(
            KotlinPropertyConverter::class.java.protectionDomain.codeSource.location
                .toURI(),
        )

    companion object {
        private const val PACKAGE = "com.tmt.input.http."
    }
}
