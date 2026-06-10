package com.freeturn.app.ui.util

/** Маскирует чувствительную строку (адреса, ключи) в privacy-режиме. */
fun String.redact(enabled: Boolean) = if (enabled) "••••••" else this
