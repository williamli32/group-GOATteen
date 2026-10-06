package com.goatteen.trading.config;

import com.goatteen.trading.recovery.RecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.*;

class StartupRecoveryConfigTest {

    @Test
    void shouldRunRecoveryServiceOnApplicationStartup() throws Exception {

        RecoveryService recoveryService = mock(RecoveryService.class);

        StartupRecoveryConfig config = new StartupRecoveryConfig();

        var runner = config.startupRecovery(recoveryService);

        ApplicationArguments arguments = new DefaultApplicationArguments();

        runner.run(arguments);

        verify(recoveryService, times(1))
                .recoverOnStartup();

        verifyNoMoreInteractions(recoveryService);
    }
}