package com.secops.strategy;

import com.secops.common.BizException;
import com.secops.common.ErrorCode;
import com.secops.model.enums.StrategySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertStrategyRegistryTest {

    private final AlertAnalysisStrategy ssh = org.mockito.Mockito.mock(AlertAnalysisStrategy.class);
    private final AlertStrategyRegistry registry;
    AlertStrategyRegistryTest() {
        org.mockito.Mockito.when(ssh.getAlertType()).thenReturn("SSH_BRUTE_FORCE");
        registry = new AlertStrategyRegistry(java.util.List.of(ssh));
    }

    @Test void routesToActualStrategyAndRejectsDuplicateRegistration() {
        assertSame(ssh, registry.getStrategy("SSH_BRUTE_FORCE"));
        assertThrows(IllegalArgumentException.class, () -> new AlertStrategyRegistry(java.util.List.of(ssh,ssh)));
        assertThrows(BizException.class, () -> registry.getStrategy("PORT_SCAN"));
    }

    @Test
    void onlySshBruteForceIsRegistered() {
        assertTrue(registry.isSupported("SSH_BRUTE_FORCE"));
        assertEquals(1, registry.supportedTypes().size());
        assertTrue(registry.supportedTypes().contains("SSH_BRUTE_FORCE"));
    }

    @Test
    void otherAlertTypesAreUnsupported() {
        for (String type : new String[]{"PORT_SCAN", "SQL_INJECTION", "MALWARE_ACTIVITY", "UNKNOWN", "DATA_SECURITY"}) {
            assertFalse(registry.isSupported(type), type + " 不应注册策略");
            assertEquals(StrategySupport.UNSUPPORTED, registry.getStrategySupport(type));
        }
        assertFalse(registry.isSupported(null));
        assertFalse(registry.isSupported(""));
    }

    @Test
    void strategySupportIsComputed() {
        assertEquals(StrategySupport.SUPPORTED, registry.getStrategySupport("SSH_BRUTE_FORCE"));
    }

    @Test
    void requireSupportedThrowsForUnsupportedType() {
        assertDoesNotThrow(() -> registry.requireSupported("SSH_BRUTE_FORCE"));
        BizException ex = assertThrows(BizException.class,
                () -> registry.requireSupported("PORT_SCAN"));
        assertEquals(ErrorCode.STRATEGY_NOT_SUPPORTED, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus());
    }
}
