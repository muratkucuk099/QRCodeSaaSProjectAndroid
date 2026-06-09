package murat.com.saasproject.util

object InviteCodeGenerator {
    private const val DEFAULT_LENGTH = 14
    private val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

    fun generate(length: Int = DEFAULT_LENGTH): String {
        return buildString(length) {
            repeat(length) {
                append(chars.random())
            }
        }
    }
}
