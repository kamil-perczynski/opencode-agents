package io.github.torvehammok

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class JsonMapperFactory {

    companion object {
        fun createJsonMapper(): JsonMapper {
            return JsonMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .build()
        }
    }

}