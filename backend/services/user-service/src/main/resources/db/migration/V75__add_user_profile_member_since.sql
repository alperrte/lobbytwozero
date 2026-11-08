-- USER-12: üyelik tarihi. Auth'un UserRegistered olayındaki occurredAt (hesabın ACTIVE olduğu an) buraya yazılır.
-- Boş olabilir: profil olaydan önce ilk istekle açıldıysa ya da olay henüz gelmediyse API created_at değerini döner.
-- Mevcut satırlara dokunulmaz (backfill yok): olay sonradan gelirse değer kesinleşir.

ALTER TABLE user_profiles ADD COLUMN member_since timestamptz;
