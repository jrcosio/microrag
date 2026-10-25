package com.lisdatasolutions.microrag.debug.feasibility

/**
 * Marks instrumented tests that need real model files on the device. They skip themselves with an
 * assumption when the files are missing, and are excluded on the phone with
 * `-e notAnnotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresModels
