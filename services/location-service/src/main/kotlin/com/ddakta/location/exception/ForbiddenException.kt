package com.ddakta.location.exception

import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

class ForbiddenException(msg: String) : ResponseStatusException(HttpStatus.FORBIDDEN, msg)
