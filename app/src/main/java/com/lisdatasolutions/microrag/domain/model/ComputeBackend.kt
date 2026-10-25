package com.lisdatasolutions.microrag.domain.model

/** Hardware a model engine runs on. NPU is out of scope: no build exists for the reference SoC. */
enum class ComputeBackend { CPU, GPU }
