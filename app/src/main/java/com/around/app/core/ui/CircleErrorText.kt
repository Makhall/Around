package com.around.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.around.app.R
import com.around.app.data.model.CircleError

@Composable
fun CircleError.userMessage(): String = stringResource(
    when (this) {
        CircleError.INVALID_NAME -> R.string.error_invalid_name
        CircleError.NAME_REQUIRED -> R.string.profile_error_name
        CircleError.CODE_REQUIRED -> R.string.error_code_required
        CircleError.INVALID_REQUEST -> R.string.error_invalid_request
        CircleError.TOO_MANY_CIRCLES -> R.string.error_too_many_circles
        CircleError.PROFILE_MISSING -> R.string.error_profile_missing
        CircleError.CODE_NOT_FOUND -> R.string.error_code_not_found
        CircleError.CODE_EXPIRED -> R.string.error_code_expired
        CircleError.CODE_REVOKED -> R.string.error_code_revoked
        CircleError.CODE_EXHAUSTED -> R.string.error_code_exhausted
        CircleError.CIRCLE_FULL -> R.string.error_circle_full
        CircleError.ALREADY_MEMBER -> R.string.error_already_member
        CircleError.RATE_LIMITED -> R.string.error_rate_limited
        CircleError.OWNER_CANNOT_LEAVE -> R.string.error_owner_cannot_leave
        CircleError.NOT_OWNER -> R.string.error_not_owner
        CircleError.MEMBER_NOT_FOUND -> R.string.error_member_not_found
        CircleError.CIRCLE_NOT_FOUND -> R.string.error_circle_not_found
        CircleError.UNAUTHENTICATED -> R.string.error_unauthenticated
        CircleError.NETWORK -> R.string.error_network
        CircleError.UNKNOWN -> R.string.error_unknown
    },
)
