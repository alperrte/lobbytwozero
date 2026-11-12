-- LIB-14: kullanıcının açık, geri alınabilir rızaları. Şimdilik tek rıza: kişiselleştirilmiş öneri için favori/bitirilen
-- oyun verisinin işlenmesi (RECOMMENDATION). Görünürlükten (privacy_settings) ayrı bir amaçtır: "koleksiyonum PUBLIC"
-- rıza sayılmaz. Satır yoksa rıza YOKTUR (varsayılan: izin yok). Geri çekilen rıza silinmez, false olarak kalır;
-- recommendation_changed_at son kararın zamanını tutar.

CREATE TABLE user_consents (
    user_id                   uuid        NOT NULL,
    recommendation_granted    boolean     NOT NULL,
    recommendation_changed_at timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL,
    version                   bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_consents PRIMARY KEY (user_id),
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES user_profiles (user_id) ON DELETE CASCADE
);
