package com.lisdatasolutions.microrag.data.download

/**
 * Hosts a model download may reach, including redirects (constitution I, F03 FR-011). A host matches
 * an entry exactly or as a subdomain, label by label, so `evilhf.co` or `hf.co.evil.com` never match.
 */
class HostAllowlist(private val domains: Set<String>) {

    fun allows(host: String?): Boolean {
        val name = host?.lowercase()?.trimEnd('.') ?: return false
        return domains.any { domain -> name == domain || name.endsWith(".$domain") }
    }

    companion object {
        /** Hugging Face and its CDN (`*.hf.co`), verified with the pinned URLs (research R2). */
        val HuggingFace = HostAllowlist(setOf("huggingface.co", "hf.co"))
    }
}
