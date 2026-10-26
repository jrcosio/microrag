package com.lisdatasolutions.microrag.data.download

/**
 * Downloads real model files from Hugging Face. Run on demand on the emulator only (quickstart §2):
 * excluded from routine runs and never run on the reference phone.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresNetwork
