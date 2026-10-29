package com.lisdatasolutions.microrag.rag.runtime

/**
 * F10 research R4: the embedded query is the previous question, a space and the question (a follow-up),
 * or the question alone. When the model rejects it as too long, the previous question is cut first with
 * the D-036 rule ([EmbeddingOverflow.cut]) and dropped once empty, then the question: the question is
 * what gets answered, the previous one only brings the topic. Only the embedded text is cut; the lexical
 * search keeps the whole search text.
 */
object QueryOverflow {

    /** The two parts of one attempt. */
    data class Step(val previous: String?, val question: String)

    /** The search text of a follow-up, shared with the follow-up rule so both join the same way. */
    fun join(previous: String?, question: String): String = if (previous == null) question else "$previous $question"

    /** What to try after the model rejected `join(previous, question)`. */
    fun next(previous: String?, question: String): Step =
        if (previous != null) {
            Step(EmbeddingOverflow.cut(previous).takeIf { it.isNotBlank() }, question)
        } else {
            Step(null, EmbeddingOverflow.cut(question))
        }
}
