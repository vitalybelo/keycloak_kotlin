package vitos.local.keycloak_kotlin.handlers

import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class ParameterChecker {

    companion object {
        val UUID_REGEX: Pattern =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    /**
     * Выполняет проверку входного параметра на соответствие шаблону идентификатора типа UUID
     *
     * @param uuid строка для проверки на валидность строковому идентификатору
     * @return true в случае удовлетворительной проверки
     */
    fun isValidUUID(uuid: CharSequence?): Boolean {
        return !uuid.isNullOrEmpty() && UUID_REGEX.matcher(uuid).matches()
    }
}