package com.goatteen.trading.auth.service;

import com.goatteen.trading.account.Account;
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
        private static final String DEFAULT_ACCOUNT_CURRENCY = "GBP";

        private final UserRepository userRepository;
        private final ClientRepository clientRepository;
        private final AccountRepository accountRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;

        public AuthService(
                        UserRepository userRepository,
                        ClientRepository clientRepository,
                        AccountRepository accountRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
                this.userRepository = userRepository;
                this.clientRepository = clientRepository;
                this.accountRepository = accountRepository;
                this.roleRepository = roleRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
        }

        @Transactional
        public RegisterResponse register(RegisterRequest request) {

                String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

                if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                        throw new DuplicateEmailException();
                }

                Role clientRole = roleRepository.findByName(CLIENT_ROLE)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Required CLIENT role is not configured"));

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
                                DEFAULT_ACCOUNT_CURRENCY);

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

        @Transactional(readOnly = true)
        public LoginResponse login(LoginRequest request) {

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

                return new LoginResponse(
                                user.getId(),
                                user.getEmail(),
                                roles,
                                accessToken);
        }
}