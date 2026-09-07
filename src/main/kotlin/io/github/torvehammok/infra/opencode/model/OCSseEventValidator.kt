package io.github.torvehammok.infra.opencode.model

import io.github.ktor_batterypack.annotation.JsonValidator
import io.github.ktor_batterypack.validation.ValidationParamType
import io.github.ktor_batterypack.validation.ValidationResult
import io.github.torvehammok.infra.ConfigMap
import tools.jackson.databind.JsonNode

@JsonValidator
interface OCSseEventValidator {

    companion object : OCSseEventValidator by OCSseEventValidatorImpl()

    @ValidationParamType(OCSseEvent::class)
    fun checkOCSseEvent(event: JsonNode): ValidationResult<JsonNode>

    @ValidationParamType(ConfigMap::class)
    fun checkConfigMap(event: JsonNode): ValidationResult<JsonNode>

}