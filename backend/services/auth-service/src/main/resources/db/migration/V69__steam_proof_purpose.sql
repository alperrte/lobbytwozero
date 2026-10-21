-- SEC-01 / N1: Steam kayıt token'ları artık iki amaçlıdır. REGISTRATION: Steam kimliğiyle hesap açma hakkı.
-- EMAIL_VERIFICATION: bekleyen Steam hesabının e-posta doğrulamasını yapanın aynı SteamID'yi kontrol ettiğinin kanıtı
-- (saldırganın kurbanın e-postasıyla açtığı hesabı kurbanın doğrulayıp saldırgana vermesini engeller).

ALTER TABLE steam_registrations ADD COLUMN purpose varchar(24) NOT NULL DEFAULT 'REGISTRATION';
ALTER TABLE steam_registrations ADD CONSTRAINT ck_steam_registrations_purpose
    CHECK (purpose IN ('REGISTRATION', 'EMAIL_VERIFICATION'));
