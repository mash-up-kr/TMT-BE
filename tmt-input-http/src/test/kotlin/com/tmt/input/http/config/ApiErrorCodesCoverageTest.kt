package com.tmt.input.http.config

import com.tmt.common.exception.ErrorType
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.lang.reflect.Method
import kotlin.test.assertTrue

/**
 * 경로 변수로 리소스를 찾는 엔드포인트는 그 리소스가 없을 때 404를 낸다. 그런데 어떤 코드로 내는지는
 * 핸들러 모양으로 알 수 없어 [ErrorResponseCustomizer]가 자동으로 붙이지 못한다 — 빠뜨리면
 * 스펙에 404가 통째로 없는 채로 나간다.
 */
class ApiErrorCodesCoverageTest {
    @Test
    fun `경로 변수를 받는 엔드포인트는 404 코드를 선언한다`() {
        val missing =
            handlerMethods()
                .filter { (path, _) -> path.contains('{') }
                .filterNot { (_, method) -> declaresNotFound(method) }
                .map { (path, method) -> "$path — ${method.declaringClass.simpleName}.${method.name}" }

        assertTrue(
            missing.isEmpty(),
            "경로 변수가 있는데 @ApiErrorCodes에 404 코드가 없다\n" + missing.joinToString("\n"),
        )
    }

    private fun declaresNotFound(method: Method): Boolean =
        method
            .getAnnotation(ApiErrorCodes::class.java)
            ?.value
            ?.any { it.errorType == ErrorType.NOT_FOUND } == true

    /** 컨트롤러 핸들러를 (전체 경로, 메서드)로 훑는다. */
    private fun handlerMethods(): List<Pair<String, Method>> =
        controllerClasses().flatMap { controller ->
            val base = pathsOf(controller).firstOrNull().orEmpty()
            controller.declaredMethods.flatMap { method ->
                pathsOf(method)
                    .ifEmpty { if (isHandler(method)) listOf("") else emptyList() }
                    .map { (base + it) to method }
            }
        }

    private fun isHandler(method: Method): Boolean =
        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java) != null

    private fun pathsOf(element: java.lang.reflect.AnnotatedElement): List<String> =
        AnnotatedElementUtils
            .findMergedAnnotation(element, RequestMapping::class.java)
            ?.path
            ?.toList()
            .orEmpty()

    private fun controllerClasses(): List<Class<*>> {
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
            .mapNotNull { runCatching { Class.forName(it, false, loader) }.getOrNull() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, RestController::class.java) }
            .toList()
    }

    private fun mainClassesRoot(): File =
        File(
            KotlinPropertyConverter::class.java.protectionDomain.codeSource.location
                .toURI(),
        )

    companion object {
        private const val PACKAGE = "com.tmt.input.http."
    }
}
