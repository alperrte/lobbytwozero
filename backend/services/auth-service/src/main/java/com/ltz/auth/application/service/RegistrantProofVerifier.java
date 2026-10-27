package com.ltz.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.ExternalIdentity;
import com.ltz.auth.domain.entity.SteamRegistration;
import com.ltz.auth.domain.enums.ExternalProvider;
import com.ltz.auth.domain.enums.SteamProofPurpose;
import com.ltz.auth.infrastructure.persistence.repository.ExternalIdentityRepository;
import com.ltz.auth.infrastructure.persistence.repository.SteamRegistrationRepository;
import com.ltz.auth.infrastructure.security.SecureTokens;

/**
 * Hesap ön-ele geçirmeyi (pre-hijacking) engeller: e-posta doğrulaması yalnız e-postanın sahibini değil, aynı
 * zamanda <b>kaydı yapanın kendisini</b> kanıtlamalıdır. Aksi halde saldırgan kurbanın e-postasıyla kendi parolası
 * veya SteamID'siyle bekleyen hesap açar, kurban kodu doğrular ve hesap saldırganın kontrolünde aktifleşir.
 * <ul>
 *   <li>Parolalı kayıt: kayıtta seçilen parola doğrulama isteğinde de gönderilmelidir.</li>
 *   <li>Steam kaydı: aynı SteamID'yi kontrol ettiğini gösteren tek kullanımlık EMAIL_VERIFICATION token'ı.</li>
 * </ul>
 * Kimlik bilgisi asla ezilmez; hesap yoksa da (veya kanıt eksikse) tam olarak bir parola doğrulaması yapılır ve aynı
 * hata döner, böylece yanıt hesap varlığını veya kanıt türünü sızdırmaz.
 */
@Component
public class RegistrantProofVerifier {

    private final PasswordEncoder passwordEncoder;
    private final ExternalIdentityRepository identityRepository;
    private final SteamRegistrationRepository steamRegistrationRepository;
    private final SecureTokens secureTokens;
    private final Clock clock;
    /** Hesap yokken veya kanıt Steam token'ıyken de aynı maliyetli doğrulamayı yapmak için açılışta üretilen hash. */
    private final String dummyPasswordHash;

    public RegistrantProofVerifier(PasswordEncoder passwordEncoder, ExternalIdentityRepository identityRepository,
                                   SteamRegistrationRepository steamRegistrationRepository,
                                   SecureTokens secureTokens, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.identityRepository = identityRepository;
        this.steamRegistrationRepository = steamRegistrationRepository;
        this.secureTokens = secureTokens;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Bekleyen hesabın kanıt gereksinimi (kısa işlemde okunur; maliyetli doğrulama işlem dışında yapılır). */
    public Requirement requirementFor(Account account) {
        String steamSubject = account.passwordHash().isPresent() ? null : identityRepository
                .findByAccountIdAndProvider(account.getId(), ExternalProvider.STEAM)
                .map(ExternalIdentity::getProviderSubject).orElse(null);
        return new Requirement(account.passwordHash().orElse(null), steamSubject);
    }

    /** @param requirement hesap yoksa {@code null} */
    public boolean isSatisfied(Requirement requirement, String password, String steamProof) {
        boolean accepted;
        if (requirement != null && requirement.passwordHash() != null) {
            // Parola gönderilmese de aynı maliyetli doğrulama yapılır (zamanlama farkı bekleyen hesabı ele vermez).
            boolean matches = passwordEncoder.matches(password == null ? "" : password, requirement.passwordHash());
            accepted = password != null && matches;
        } else {
            // Maliyeti eşitlemek için tam bir doğrulama; sonucu yok sayılır.
            passwordEncoder.matches(password == null ? "" : password, dummyPasswordHash);
            accepted = requirement != null && requirement.steamSubject() != null && steamProof != null
                    && steamProofMatches(steamProof, requirement.steamSubject());
        }
        return accepted;
    }

    /** Steam kanıtını işlem içinde tüketir (kod doğrulaması başarılıyken). Parola kanıtında yapılacak bir şey yoktur. */
    public boolean consumeSteamProof(Requirement requirement, String steamProof) {
        if (requirement.passwordHash() != null) {
            return true;
        }
        Instant now = clock.instant();
        Optional<SteamRegistration> proof = steamRegistrationRepository
                .findByTokenHashForUpdate(secureTokens.sha256Hex(steamProof))
                .filter(candidate -> candidate.isUsableAt(now, SteamProofPurpose.EMAIL_VERIFICATION))
                .filter(candidate -> candidate.getSteamId().equals(requirement.steamSubject()));
        proof.ifPresent(candidate -> candidate.consume(now));
        return proof.isPresent();
    }

    private boolean steamProofMatches(String steamProof, String steamSubject) {
        Instant now = clock.instant();
        return steamRegistrationRepository.findById(secureTokens.sha256Hex(steamProof))
                .filter(candidate -> candidate.isUsableAt(now, SteamProofPurpose.EMAIL_VERIFICATION))
                .filter(candidate -> candidate.getSteamId().equals(steamSubject))
                .isPresent();
    }

    public record Requirement(String passwordHash, String steamSubject) {

        @Override
        public String toString() {
            return "Requirement[passwordHash=***, steamSubject=***]";
        }
    }
}
