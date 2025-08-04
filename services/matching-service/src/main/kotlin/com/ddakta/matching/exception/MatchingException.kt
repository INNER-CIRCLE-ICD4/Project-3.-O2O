package com.ddakta.matching.exception

import com.ddakta.utils.exception.BusinessException
import org.springframework.http.HttpStatus

open class MatchingException(
    errorCode: String,
    message: String,
    httpStatus: HttpStatus = HttpStatus.BAD_REQUEST
) : BusinessException(errorCode, message, httpStatus)