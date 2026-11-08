-- CONT-10: moderasyon kararını veren kullanıcının, admin-service tarafından doğrulanmış rolü kayda yazılır; doğrudan moderatör
-- yazma ucu kaldırıldığı için karar yolu yalnız ADMIN_SERVICE'tir. V106 uygulanmış olduğundan değiştirilmez.
-- actor_role: yalnız yeni kayıtlarda uygulama tarafından doldurulur (eski kayıt yoktur; sütun geriye uyumluluk için null kabul eder).
-- source kısıtı NOT VALID eklenir: yeni satırlar denetlenir, olası eski satırlar taranmaz ve migration'ı durdurmaz.

alter table content_moderation_actions
    add column actor_role varchar(16);

alter table content_moderation_actions
    add constraint ck_content_moderation_actions_actor_role check (actor_role is null or actor_role in ('MODERATOR', 'ADMIN'));

alter table content_moderation_actions
    drop constraint ck_content_moderation_actions_source;

alter table content_moderation_actions
    add constraint ck_content_moderation_actions_source check (source = 'ADMIN_SERVICE') not valid;
