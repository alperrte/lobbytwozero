-- Yalnız izole aktarım provası. Gerçek DEV verisi değildir.
insert into games(id,title,normalized_title,created_at,updated_at,version,origin,metadata_completeness,status,
 game_type,description,release_date,details_fetched_at,details_expires_at,details_next_attempt_at,
 details_failure_count,details_last_error_code,early_access,editor_rank,popularity_updated_at,
 editor_actor_id,editor_evidence,metadata_language,language_data_state)
values ('10000000-0000-0000-0000-000000000001','Synthetic Atlas','synthetic atlas',
 '2026-10-01 12:34:56.123456+03','2026-10-02 10:00:00.654321+00',7,'EXTERNAL','PARTIAL','ACTIVE',
 'MAIN_GAME','Synthetic catalog description','2025-05-01','2026-10-02 10:00:00+00',
 '2026-10-03 10:00:00+00','2026-10-04 10:00:00+00',2,'TIMEOUT',true,1,'2026-10-02 10:00:00+00',
 '20000000-0000-0000-0000-000000000001','Synthetic editorial evidence','tr','AVAILABLE'),
 ('10000000-0000-0000-0000-000000000002','Synthetic Orbit','synthetic orbit',
 '2026-09-01 00:00:00+00','2026-10-01 00:00:00+00',3,'INTERNAL','INDEX_ONLY','ACTIVE',
 'MAIN_GAME',null,null,null,null,null,0,null,false,null,null,null,null,null,'UNKNOWN');

insert into catalog_terms(id,kind,code,name_tr,name_en,version) values
 ('30000000-0000-0000-0000-000000000001','GENRE','adventure','Macera','Adventure',4),
 ('30000000-0000-0000-0000-000000000002','CATEGORY','single-player','Tek oyuncu','Single player',2),
 ('30000000-0000-0000-0000-000000000003','TAG','exploration','Kesif','Exploration',1),
 ('30000000-0000-0000-0000-000000000004','PLATFORM','windows','Windows','Windows',6);
insert into game_terms(game_id,term_id) select '10000000-0000-0000-0000-000000000001',id from catalog_terms;
insert into game_developers values ('10000000-0000-0000-0000-000000000001','Synthetic Studio');
insert into game_publishers values ('10000000-0000-0000-0000-000000000001','Synthetic Publisher');
insert into game_media values ('40000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000001','COVER',
 'https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/123456/header.jpg','STEAM',0,5);
insert into game_system_requirements values ('40000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000001','MINIMUM','windows','Synthetic OS','Synthetic CPU',
 'Synthetic GPU',8192,20480,'Synthetic requirements',8);
insert into game_languages values ('10000000-0000-0000-0000-000000000001','tr'),
 ('10000000-0000-0000-0000-000000000001','en');
insert into game_external_refs values ('50000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000001','STEAM','123456','2026-10-02 10:00:00.123456+00',11);
update game_provider_sync_state set cursor=991231,run_mode='INCREMENTAL',sync_status='FAILED',
 query_since='2026-10-01 09:00:00+00',run_started_at='2026-10-02 09:00:00+00',
 last_successful_at='2026-10-01 10:00:00+00',processed_count=30,last_error_code='TIMEOUT',
 version=19,failure_count=3,next_attempt_at='2026-10-02 11:00:00+00' where provider='STEAM';
insert into game_identity_audits values ('60000000-0000-0000-0000-000000000001',
 '50000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
 'MAP','Synthetic verified mapping evidence','2026-10-02 10:00:00.123456+00');
insert into game_field_overrides values ('60000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000001','LOCALIZATION',
 '20000000-0000-0000-0000-000000000001','2026-10-02 10:00:00+00',12);
insert into game_admin_audits values ('60000000-0000-0000-0000-000000000003',
 '10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',
 'ADMIN','UPDATE_GAME','Synthetic manual correction','fields=LOCALIZATION','2026-10-02 10:00:00+00');
insert into game_developer_grants values
 ('70000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001',
 '2026-09-01 00:00:00+00',null,null,4),
 ('70000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000001',
 '2026-09-01 00:00:00+00','20000000-0000-0000-0000-000000000001','2026-09-02 00:00:00+00',9);
insert into game_developer_contents values ('10000000-0000-0000-0000-000000000001',
 'Synthetic developer description','20000000-0000-0000-0000-000000000002','VISIBLE',
 null,null,null,'2026-09-01 00:00:00+00','2026-10-02 00:00:00+00',13);
insert into game_developer_media values ('80000000-0000-0000-0000-000000000001',
 '10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002','IMAGE',
 'https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/123456/header.jpg','Synthetic cover',
 'VISIBLE',null,null,null,'2026-09-01 00:00:00+00','2026-10-02 00:00:00+00',14);
insert into game_update_notes values ('80000000-0000-0000-0000-000000000002',
 '10000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002',
 'Synthetic update','Synthetic update body','VISIBLE',null,null,null,
 '2026-09-01 00:00:00+00','2026-10-02 00:00:00+00',15);
insert into game_language_supports values ('10000000-0000-0000-0000-000000000001','tr','SUPPORTED','UNKNOWN','SUPPORTED'),
 ('10000000-0000-0000-0000-000000000001','en','SUPPORTED','SUPPORTED','UNSUPPORTED');
insert into game_metadata_provenance values ('10000000-0000-0000-0000-000000000001','TITLE','ADMIN','AVAILABLE',
 '2026-10-02 10:00:00.123456+00',null),
 ('10000000-0000-0000-0000-000000000001','LOCALIZATION','ADMIN','AVAILABLE','2026-10-02 10:00:00+00',null);
insert into game_achievement_definitions values
 ('90000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','first-step',
 'Synthetic first step','Synthetic achievement',true,16,'2026-09-01 00:00:00+00','2026-10-02 00:00:00+00'),
 ('90000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','hidden-step',
 'Synthetic hidden step',null,false,17,'2026-09-01 00:00:00+00','2026-10-02 00:00:00+00');
insert into game_developer_feedback values
 ('a0000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000004','BUG_REPORT','IN_PROGRESS',
 'Synthetic private issue','Synthetic private feedback body',18,'2026-09-01 00:00:00.123456+00','2026-10-02 00:00:00+00'),
 ('a0000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000004','EARLY_ACCESS','RESOLVED',
 'Synthetic early access issue','Synthetic private early access body',20,'2026-09-01 00:00:00+00','2026-10-02 00:00:00+00');
