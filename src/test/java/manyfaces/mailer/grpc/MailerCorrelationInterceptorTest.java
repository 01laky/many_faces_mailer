package manyfaces.mailer.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MailerCorrelationInterceptorTest {

    @Test
    void sanitize_rejectsNewlines() {
        assertEquals(null, MailerCorrelationInterceptor.sanitize("a\r\nb"));
    }

    @Test
    void sanitize_trimsAndAcceptsPlainValue() {
        assertEquals("corr-9", MailerCorrelationInterceptor.sanitize("  corr-9  "));
    }

    @Test
    void sanitize_rejectsNullByte() {
        assertEquals(null, MailerCorrelationInterceptor.sanitize("a\u0000b"));
    }

    @Test
    void traceIdFromTraceparent_parsesW3cExample() {
        Optional<String> tid = MailerCorrelationInterceptor.traceIdFromTraceparentForTests(
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        assertTrue(tid.isPresent());
        assertEquals("0af7651916cd43dd8448eb211c80319c", tid.get());
    }

    @Test
    void traceIdFromTraceparent_acceptsUppercaseHexAfterNormalization() {
        Optional<String> tid = MailerCorrelationInterceptor.traceIdFromTraceparentForTests(
                "00-0AF7651916CD43DD8448EB211C80319C-b7ad6b7169203331-01");
        assertTrue(tid.isPresent());
        assertEquals("0af7651916cd43dd8448eb211c80319c", tid.get());
    }

    @Test
    void traceIdFromTraceparent_rejectsBadLength() {
        assertFalse(MailerCorrelationInterceptor.traceIdFromTraceparentForTests("00-short-b7-01").isPresent());
    }
}
