package com.ddakta.utils.validation

object EnumUtil {

    /**
     * 문자열을 Enum으로 변환 (대소문자 무시, alias, 기본값 지원)
     *
     * @param T 변환 대상 Enum 타입
     * @param input 입력 문자열
     * @param default 기본값 (null 허용)
     */
    inline fun <reified T : Enum<T>> String.toEnumOrNull(
        default: T? = null
    ): T? {
        val normalized = this.trim().lowercase()
        return enumValues<T>().firstOrNull {
            it.name.equals(normalized, ignoreCase = true)
        } ?: default
    }
}
