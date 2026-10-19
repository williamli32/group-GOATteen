package com.goatteen.trading.auth;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.auth.dto.RegisterRequest;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.auth.service.AuthService;
import com.goatteen.trading.auth.service.RefreshTokenService;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.role.Role;
import com.goatteen.trading.role.RoleRepository;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceRegistrationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private AuthService service;

    @BeforeEach
    void setup() {
        service = new AuthService(users, clients, accounts, roles, passwords,
                mock(JwtService.class), mock(RefreshTokenService.class));
        when(roles.findByName(anyString())).thenReturn(Optional.of(mock(Role.class)));
        when(passwords.encode(anyString())).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(clients.save(any())).thenAnswer(call -> call.getArgument(0));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private RegisterRequest request(String role, String currency) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test"); request.setLastName("User");
        request.setEmail("TEST@example.com"); request.setPassword("Test-password-123!");
        request.setRole(role); request.setCurrency(currency);
        request.setAccessCode((String) ReflectionTestUtils.getField(AuthService.class, "ANALYST_ACCESS_CODE"));
        return request;
    }

    @ParameterizedTest
    @CsvSource({"CLIENT,USD", "CLIENT,GBP", "CLIENT,INR", "ANALYST,USD", "ANALYST,GBP", "ANALYST,INR",
            "OPERATIONS,USD", "OPERATIONS,GBP", "OPERATIONS,INR"})
    void createsEachRoleWithTheSelectedCurrency(String role, String currency) {
        var result = service.register(request(role, currency));
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        verify(roles).findByName(role);
        assertEquals(currency, saved.getValue().getCurrency());
        assertEquals(currency, result.getCurrency());
        assertEquals(0, saved.getValue().getCashBalance().signum());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYST", "OPERATIONS"})
    void requiresTheSharedAccessCodeForBothStaffRoles(String role) {
        RegisterRequest request = request(role, "USD");
        request.setAccessCode("incorrect-code");
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(users, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @Test
    void customerNeedsNoAccessCode() {
        RegisterRequest request = request("CLIENT", "USD");
        request.setAccessCode(null);
        assertDoesNotThrow(() -> service.register(request));
    }

    @Test
    void cannotRegisterAsAdministratorByCallingTheServiceDirectly() {
        assertThrows(IllegalArgumentException.class, () -> service.register(request("ADMIN", "USD")));
        verify(roles, never()).findByName(anyString());
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "BTC", "INVALID", "EUR", "JPY", "CHF", "AUD", "CAD", "NZD", "SGD"})
    void rejectsUnsupportedOrMissingCurrenciesBeforeCreatingRecords(String currency) {
        assertThrows(IllegalArgumentException.class, () -> service.register(request("CLIENT", currency)));
        verify(users, never()).save(any());
        verify(accounts, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYST", "OPERATIONS"})
    void staffCannotBypassTheRegistrationCurrencyRestriction(String role) {
        assertThrows(IllegalArgumentException.class, () -> service.register(request(role, "JPY")));
        verify(users, never()).save(any());
        verify(accounts, never()).save(any());
    }
}
