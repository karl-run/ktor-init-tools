package io.ktor.start.swagger

import io.ktor.start.BuildInfo
import io.ktor.start.util.*

object SwaggerGeneratorCommon {

    fun BlockBuilder.fileSwaggerBackendTests(fileName: String, info: BuildInfo, model: SwaggerModel) {
        fileText(fileName) {
            SEPARATOR {
                +"package ${info.artifactGroup}"
            }
            SEPARATOR {
                +"import java.util.*"
                +"import io.ktor.config.*"
                +"import io.ktor.http.*"
                +"import io.ktor.request.*"
                +"import io.ktor.server.testing.*"
                +"import io.ktor.swagger.experimental.*"
                +"import kotlin.test.*"
            }
            SEPARATOR {
                +"class SwaggerRoutesTest" {
                    for (path in model.paths.values) {
                        for (method in path.methods.values) {
                            SEPARATOR {
                                +"/**"
                                +" * @see ${model.info.classNameServer}.${method.methodName}"
                                +" */"
                                +"@Test"
                                +"fun test${method.methodName.capitalize()}()" {
                                    +"withTestApplication" {
                                        +"// @TODO: Adjust path as required"
                                        +"handleRequest(HttpMethod.${method.method.toLowerCase().capitalize()}, \"${method.path}\") {"
                                        when (method.method.toUpperCase()) {
                                            "POST", "PUT" -> indent {
                                                +"// @TODO: Your body here"
                                                +"setBodyJson(mapOf<String, Any?>())"
                                            }
                                        }
                                        +"}.apply {"
                                        indent {
                                            +"// @TODO: Your test here"
                                            +"assertEquals(HttpStatusCode.OK, response.status())"
                                        }
                                        +"}"
                                    }
                                }
                            }
                        }
                    }
                    SEPARATOR {
                        +"fun <R> withTestApplication(test: TestApplicationEngine.() -> R): R" {
                            +"return withApplication(createTestEnvironment())" {
                                +"(environment.config as MapApplicationConfig).apply" {
                                    +"put(\"jwt.secret\", \"TODO-change-this-supersecret-or-use-SECRET-env\")"
                                }
                                +"application.module()"
                                +"test()"
                            }
                        }
                    }

                    SEPARATOR {
                        +"fun TestApplicationRequest.setBodyJson(value: Any?) = setBody(Json.stringify(value))"
                    }
                }
            }
        }
    }


    fun BlockBuilder.filesHttpApi(
        apiFileName: String,
        envJsonFileName: String,
        modelSourceFileName: String,
        info: BuildInfo,
        model: SwaggerModel
    ) {
        fileText(modelSourceFileName) {
            +model.source
        }

        fileText(envJsonFileName) {
            val paramsInUrls = model.paths.values.flatMap { it.methodsList }
                .flatMap { Regex("\\{(\\w+)\\}").findAll(it.path).map { it.groupValues[1] }.toList() }.toSet()

            +Json.encodePrettyUntyped(
                mapOf(
                    "localhost" to mapOf(
                        "host" to "http://127.0.0.1:8080"
                    ) + paramsInUrls.map { "param_$it" to it }.toMap()
                    //"prod" to mapOf(
                    //    "host" to "https://my.domain.com"
                    //)
                ), "    "
            )
        }

        fileText(apiFileName) {
            +"# ${model.info.title.stripLineBreaks()}"
            for (descLine in model.info.description.lines()) {
                +"# $descLine"
            }
            +""
            for (path in model.paths.values) {
                for (method in path.methodsList) {
                    val httpMethod = method.method.toUpperCase()
                    +"###"
                    +""

                    for (descLine in method.summaryDescription.lines()) {
                        +"# $descLine"
                    }

                    val escapedPath = path.path.replace(Regex("\\{(\\w+)\\}")) { matchResult ->
                        "{{param_${matchResult.groupValues[1]}}}"
                    }

                    // @TODO: Escaping when required?
                    val queryString = method.parametersQuery
                        .filter { it.default != null }
                        .joinToString("&") { it.name + "=" + it.schema.type.toKotlinDefault(it.default, typed = false) }

                    val rqueryString = if (queryString.isNotEmpty()) "?$queryString" else ""

                    +"$httpMethod {{host}}$escapedPath$rqueryString"
                    for ((sec, secdef) in method.securityDefinitions(model).filter {
                        it.second?.inside == "header" && it.second?.type == SwaggerModel.SecurityType.API_KEY
                    }) {
                        +"${secdef!!.name}: Bearer {{ auth_token }}"
                    }
                    if (httpMethod == "POST" || httpMethod == "PUT") {
                        val requestBody = method.requestBodyMerged.firstOrNull()
                        if (requestBody != null) {
                            val postBody = requestBody.schema.type.toDefaultUntyped()
                            val contentType = requestBody.contentType
                            +"Content-Type: $contentType"
                            +""
                            when (contentType) {
                                ContentType.ApplicationJson -> {
                                    +Json.encodePrettyUntyped(postBody)
                                }
                                ContentType.ApplicationXWwwFormUrlencoded -> {
                                    +Dynamic { postBody.strEntries.map { it.first to it.second.str }.formUrlEncode() }
                                }
                                else -> {
                                    +"# Unsupported contentType=$contentType"
                                }
                            }
                        }
                    }
                    +""
                    val loginRoute = method.tryGetCompatibleLoginRoute()
                    if (loginRoute != null) {
                        val tokenPath = loginRoute.tokenPath
                        val responsePath = "response.body.${tokenPath.joinToString(".")}"
                        +"> {%"
                        +"client.assert(typeof $responsePath !== \"undefined\", \"No token returned\");"
                        +"client.global.set(\"auth_token\", $responsePath);"
                        +"%}"
                        +""
                    }
                }
            }
        }
    }

}