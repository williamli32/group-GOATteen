package com.goatteen.trading.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(
            min = 12,
            max = 72,
            message = "Password must be between 12 and 72 characters"
    )
    private String password;

    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "CLIENT|ANALYST|OPERATIONS", message = "Select customer, analyst or operations")
    private String role = "CLIENT";

    @NotBlank(message = "Account currency is required")
    @Size(min = 3, max = 3, message = "Use a three-letter account currency code")
    private String currency;

    private String accessCode;

    public RegisterRequest() {
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
