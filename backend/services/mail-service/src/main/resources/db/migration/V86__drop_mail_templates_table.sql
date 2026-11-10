-- Şablonlar artık dosyadır: src/main/resources/mail/templates/<TÜR>.<dil>.mail (açılışta MailTemplateCatalog doğrular).
-- Veritabanındaki mail_templates tablosu (V81..V85 ile doldurulmuştu) kaldırılır; V81..V85 değiştirilmez.

DROP TABLE mail_templates;
