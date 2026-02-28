package com.privacyshield.llm

data class Message(
    val content: String,
    val isUser: Boolean,
    val id: Long = System.nanoTime()
)

/**
 * Manages conversation history with a hard token budget.
 *
 * Strategy: keep the system prompt + the most recent [maxMessages] turns.
 * When the limit is exceeded, drop the oldest user/assistant pair.
 */
class ConversationManager(
    private val maxMessages: Int = 20  // ~10 turns
) {
    private val _messages = mutableListOf<Message>()
    val messages: List<Message> get() = _messages.toList()

    fun addUserMessage(text: String) {
        _messages += Message(content = text, isUser = true)
        trim()
    }

    fun addAssistantMessage(text: String) {
        _messages += Message(content = text, isUser = false)
        trim()
    }

    fun clear() = _messages.clear()

    private fun trim() {
        while (_messages.size > maxMessages) {
            // Always remove oldest pair to maintain coherence
            _messages.removeAt(0)
        }
    }
}
