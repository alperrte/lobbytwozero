package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.auth.application.port.VerificationCodeMail;
import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.Role;
import com.ltz.auth.domain.enums.RoleCode;
import com.ltz.auth.domain.enums.SecurityEventType;
import com.ltz.auth.domain.exception.UsernameAlreadyTakenException;
import com.ltz.auth.infrastructure.persistence.repository.AccountRepository;
import com.ltz.auth.infrastructure.persistence.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AUTH-01: hesap PENDING_VERIFICATION durumunda, yalnız PLAYER rolüyle oluşturulur.
 * Tekillik uygulama kontrolüne değil DB constraint'lerine dayanır; yarışta tek hesap kalır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    static final String EMAIL_UNIQUE_CONSTRAINT = "uq_accounts_email_normalized";
    static final String USERNAME_UNIQUE_CONSTRAINT = "uq_accounts_username_normalized";
    private static final RoleCode INITIAL_ROLE = RoleCode.PLAYER;

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final SecurityEventRecorder securityEventRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Kayıtlı bir e-posta için de normal dönüş yapar (K1). Parola her iki durumda da hash'lenir;
     * yanıt süresi e-postanın kayıtlı olup olmadığını belli etmez.
     */
    public void register(String email, String username, String password) {
        if (accountRepository.existsByUsernameNormalized(Account.normalizeUsername(username))) {
            throw new UsernameAlreadyTakenException();
        }
        String passwordHash = passwordEncoder.encode(password);
        VerificationCodeMail mail;
        try {
            mail = transactionTemplate.execute(status -> createPendingAccount(email, username, passwordHash));
        } catch (DataIntegrityViolationException exception) {
            handleUniqueViolation(exception, email);
            return;
        }
        emailVerificationService.dispatch(mail);
    }

    private VerificationCodeMail createPendingAccount(String email, String username, String passwordHash) {
        Role initialRole = roleRepository.findByCode(INITIAL_ROLE)
                .orElseThrow(() -> new IllegalStateException("Initial role seed is missing: " + INITIAL_ROLE));
        Instant now = clock.instant();
        Account account = accountRepository.saveAndFlush(
                Account.registerPending(email, username, passwordHash, initialRole, now));
        securityEventRecorder.record(SecurityEventType.ACCOUNT_REGISTERED, account.getId());
        log.info("Account registered accountId={}", account.getId());
        return emailVerificationService.issueCode(account, now);
    }

    private void handleUniqueViolation(DataIntegrityViolationException exception, String email) {
        String constraint = violatedConstraint(exception);
        if (USERNAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraint)) {
            throw new UsernameAlreadyTakenException();
        }
        if (!EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraint)) {
            throw exception;
        }
        UUID existingAccountId = accountRepository.findByEmailNormalized(Account.normalizeEmail(email))
                .map(Account::getId)
                .orElse(null);
        securityEventRecorder.record(SecurityEventType.REGISTRATION_EMAIL_CONFLICT, existingAccountId);
        log.info("Registration rejected for an already registered email accountId={}", existingAccountId);
    }

    private static String violatedConstraint(DataIntegrityViolationException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
