package io.casehub.blocks.speech.sherpa.correction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleMetaphoneTest {

    private final DoubleMetaphone dm = new DoubleMetaphone();

    @Test
    void commonWordsProduceStableCodes() {
        assertThat(dm.compute("hello")[0]).isEqualTo(dm.compute("hello")[0]);
    }

    @Test
    void codeHasMaxLength4() {
        String[] codes = dm.compute("international");
        assertThat(codes[0]).hasSizeLessThanOrEqualTo(4);
        assertThat(codes[1]).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void caseInsensitive() {
        assertThat(dm.compute("Hello")[0]).isEqualTo(dm.compute("hello")[0]);
    }

    @Test
    void emptyStringReturnsEmptyCodes() {
        String[] codes = dm.compute("");
        assertThat(codes[0]).isEmpty();
        assertThat(codes[1]).isEmpty();
    }

    @Test
    void singleCharacterProducesCode() {
        String[] codes = dm.compute("a");
        assertThat(codes[0]).isNotEmpty();
    }

    @Test
    void smithAndSmytheShareCode() {
        String[] smith = dm.compute("smith");
        String[] smythe = dm.compute("smythe");
        assertThat(smith[0]).isEqualTo(smythe[0]);
    }

    @Test
    void knightAndNightShareCode() {
        String[] knight = dm.compute("knight");
        String[] night = dm.compute("night");
        assertThat(knight[0]).isEqualTo(night[0]);
    }

    @Test
    void phoneAndFoneShareCode() {
        String[] phone = dm.compute("phone");
        String[] fone = dm.compute("fone");
        assertThat(phone[0]).isEqualTo(fone[0]);
    }

    @Test
    void distinctWordsProdüceDifferentCodes() {
        String[] cat = dm.compute("cat");
        String[] dog = dm.compute("dog");
        assertThat(cat[0]).isNotEqualTo(dog[0]);
    }

    @Test
    void primaryAndAlternateCanDiffer() {
        // "Michael" typically produces different primary and alternate
        String[] codes = dm.compute("michael");
        assertThat(codes).hasSize(2);
    }

    @Test
    void writeAndRightShareCode() {
        String[] write = dm.compute("write");
        String[] right = dm.compute("right");
        assertThat(write[0]).isEqualTo(right[0]);
    }
}
