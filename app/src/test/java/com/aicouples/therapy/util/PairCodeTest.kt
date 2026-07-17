package com.aicouples.therapy.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairCodeTest {
    @Test
    fun normalize_uppercasesAndTrims() {
        assertThat(PairCode.normalize(" abf39q ")).isEqualTo("ABF39Q")
    }

    @Test
    fun isValid_requiresSixChars() {
        assertThat(PairCode.isValid("ABF39Q")).isTrue()
        assertThat(PairCode.isValid("ABC")).isFalse()
    }
}
