package com.aicouples.therapy

import com.aicouples.therapy.common.PairCode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PairCodeTest {
    @Test
    fun normalize_uppercases_and_trims() {
        assertThat(PairCode.normalize(" abf39q ")).isEqualTo("ABF39Q")
    }

    @Test
    fun isValid_requires_six_chars() {
        assertThat(PairCode.isValid("ABF39Q")).isTrue()
        assertThat(PairCode.isValid("ABC")).isFalse()
        assertThat(PairCode.isValid("ABF39QZ")).isFalse()
    }

    @Test
    fun normalize_strips_invalid_characters() {
        assertThat(PairCode.normalize("AB-F3 9Q")).isEqualTo("ABF39Q")
    }
}
