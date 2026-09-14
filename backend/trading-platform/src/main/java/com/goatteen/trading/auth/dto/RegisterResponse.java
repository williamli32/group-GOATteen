package com.goatteen.trading.auth.dto;

public class RegisterResponse {

    private final String email;
    private final String firstName;
    private final String lastName;
    private final String accountNumber;
    private final String currency;

    public RegisterResponse(
            String email,
            String firstName,
            String lastName,
            String accountNumber,
            String currency
    ) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.accountNumber = accountNumber;
        this.currency = currency;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }
}