package com.rorapps.eprid.dto.common

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val errors: List<String>? = null
) {
    companion object {
        fun <T> ok(data: T, message: String? = null) = ApiResponse(success = true, data = data, message = message)
        fun <T> error(message: String, errors: List<String>? = null) =
            ApiResponse<T>(success = false, message = message, errors = errors)
    }
}
