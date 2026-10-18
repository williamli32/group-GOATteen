package com.goatteen.trading.auth.service;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.account.AccountCurrency;
import com.goatteen.trading.account.AccountRepository;
import com.goatteen.trading.auth.dto.RegisterRequest;
import com.goatteen.trading.auth.dto.RegisterResponse;
import com.goatteen.trading.auth.exception.DuplicateEmailException;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.client.ClientRepository;
import com.goatteen.trading.role.Role;
import com.goatteen.trading.role.RoleRepository;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.goatteen.trading.auth.dto.LoginRequest;
import com.goatteen.trading.auth.dto.LoginResponse;
import com.goatteen.trading.auth.exception.InvalidCredentialsException;
import com.goatteen.trading.auth.security.JwtService;

import java.util.Set;
import java.util.stream.Collectors;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

        private static final String CLIENT_ROLE = "CLIENT";
        private static final String ANALYST_ROLE = "ANALYST";
        private static final String OPERATIONS_ROLE = "OPERATIONS";
        private static final String ANALYST_ACCESS_CODE = "goatteen14";

        private final UserRepository userRepository;
        private final ClientRepository clientRepository;
        private final AccountRepository accountRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;

        public AuthService(
                        UserRepository userRepository,
                        ClientRepository clientRepository,
                        AccountRepository accountRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RefreshTokenService refreshTokenService) {
                this.userRepository = userRepository;
                this.clientRepository = clientRepository;
                this.accountRepository = accountRepository;
                this.roleRepository = roleRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.refreshTokenService = refreshTokenService;
        }

        @Transactional
        public RegisterResponse register(RegisterRequest request) {

                String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

                if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                        throw new DuplicateEmailException();
                }

                String requestedRole = request.getRole() == null
                                ? CLIENT_ROLE
                                : request.getRole().trim().toUpperCase(Locale.ROOT);

                if (!Set.of(CLIENT_ROLE, ANALYST_ROLE, OPERATIONS_ROLE).contains(requestedRole)) {
                        throw new IllegalArgumentException("Select customer, analyst or operations");
                }

                if ((ANALYST_ROLE.equals(requestedRole) || OPERATIONS_ROLE.equals(requestedRole))
                                && !ANALYST_ACCESS_CODE.equals(request.getAccessCode())) {
                        throw new IllegalArgumentException(
                                        "A valid analyst/operations access code is required");
                }

                AccountCurrency currency = AccountCurrency.forRegistration(request.getCurrency());

                Role clientRole = roleRepository.findByName(requestedRole)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Required registration role is not configured"));

                String passwordHash = passwordEncoder.encode(request.getPassword());

                User user = new User(
                                normalizedEmail,
                                passwordHash);

                user.addRole(clientRole);
                user = userRepository.save(user);

                Client client = new Client(
                                user,
                                request.getFirstName().trim(),
                                request.getLastName().trim());

                client = clientRepository.save(client);

                String accountNumber = generateAccountNumber();

                Account account = new Account(
                                client,
                                accountNumber,
                                currency.name());

                account = accountRepository.save(account);

                return new RegisterResponse(
                                user.getEmail(),
                                client.getFirstName(),
                                client.getLastName(),
                                account.getAccountNumber(),
                                account.getCurrency());
        }

        private String generateAccountNumber() {

                String accountNumber;

                do {
                        accountNumber = "LEAP-" +
                                        UUID.randomUUID()
                                                        .toString()
                                                        .replace("-", "")
                                                        .substring(0, 12)
                                                        .toUpperCase(Locale.ROOT);

                } while (accountRepository.existsByAccountNumber(accountNumber));

                return accountNumber;
        }

        public LoginResult login(LoginRequest request) {

                String normalizedEmail = request.getEmail()
                                .trim()
                                .toLowerCase(Locale.ROOT);

                User user = userRepository
                                .findByEmailIgnoreCase(normalizedEmail)
                                .orElseThrow(InvalidCredentialsException::new);

                if (!user.isEnabled()) {
                        throw new InvalidCredentialsException();
                }

                if (!passwordEncoder.matches(
                                request.getPassword(),
                                user.getPasswordHash())) {
                        throw new InvalidCredentialsException();
                }

                Set<String> roles = user.getRoles()
                                .stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet());

                String accessToken = jwtService.generateAccessToken(user);

                RefreshTokenService.RefreshTokenResult refresh = refreshTokenService.createSession(user);

                LoginResponse response = new LoginResponse(
                                user.getId(),
                                user.getEmail(),
                                roles,
                                accessToken);

                return new LoginResult(
                                response,
                                refresh.token(),
                                refresh.expiresAt());
        }

        public record LoginResult(
                        LoginResponse response,
                        String refreshToken,
                        java.time.LocalDateTime refreshTokenExpiresAt) {
        }

        public String generateAccessToken(User user) {
                return jwtService.generateAccessToken(user);
        }
}
