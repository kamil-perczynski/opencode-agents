package io.github.torvehammok.infra.git

import io.github.ktor_batterypack.core.exception.ErrorCode

enum class GitConfigSyncErrorCode(override val message: String) : ErrorCode {
    GIT_CLONE_TARGET_DIR_EXISTS("Directory where git repository should be cloned already exists");

    override val code: String = name
}