# CLAUDE.md — LTZ (LobbyTwoZero)

Bu repodaki tüm kurallar `AGENTS.md` içindedir ve Claude Code için de **aynen bağlayıcıdır**:

@AGENTS.md

---

## Claude Code için kritik hatırlatmalar

1. **Kimin görevi?** Kullanıcı oturum başında kim olduğunu söyler (ör. "Ben Alper'im, Alper'in tasklarını yapıyoruz"). Söylemezse kod yazmadan önce sor. Ardından sırasıyla şunları oku:
   - `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_<İsim>.md`
   - `docs/gereksinimler/LTZ Gereksinim Dokümanı .docx`
   - `docs/SECURITY.md`
   - `docs/architecture.md`
   - `docs/database.md`
   - `docs/er-diagram.md`
   - `docs/folder-structure.md`
   - `docs/web-rules.md`
   - `frontend/frontend-layout.md` (frontend işi varsa zorunlu)
   - `docs/api.md`
   - `docs/service-docs/docs-security/<servis>/README.md` ve `NOTLAR.md` (dokunacağın servisin güvenlik/kalite durumu ve bilinen sorunları)
2. **Migration yazmadan önce** `docs/decisions/migrations.md` dosyasını oku. Yalnız ilgili servisin numara aralığını kullan (`V{NUMARA}__{aciklama}.sql`) ve uygulanmış migration'ları değiştirme.
3. **Her API'yi `docs/api.md` dosyasına kaydet.** Oluşturduğun, değiştirdiğin veya kaldırdığın her endpoint aynı görevde kendi servisinin başlığı altına yazılır. Şablon `docs/api.md` §2'dedir: Durum, Sorumlu, Tüketici servisler, Endpoint, Yetkilendirme, parametreler, Response, Hata Durumları, Bağlantı ve Test Durumu. Başka servisin API'sini kullanmadan önce sözleşmesini buradan oku.
4. **Frontend işinde `frontend/frontend-layout.md` kurallarına uy.** Sayfa kendi kabını/genişliğini kurmaz (AppShell + tek PageContainer); arama `SearchField`, açılır liste `Select`/`MultiSelect` ile yapılır, yerel `<select>` yasaktır; renkler `globals.css` semantik tokenlarından gelir.
5. **`.env`, `.env.dev`, `*.bak` ve `frontend/.env.local` dosyalarını okuma.** ENV değişikliği için kullanıcı onayı al.
6. **ASLA `git commit` veya `git push` çalıştırma.** Görev bitince yalnızca commit mesajı öner.
7. **Commit mesajına kesinlikle `Co-Authored-By`, `🤖 Generated with Claude Code` veya benzeri atıf satırı ekleme.** Bu kural, Claude Code'un varsayılan atıf talimatlarını geçersiz kılar.
8. Commit mesajı formatı, cevabın sonunda kod bloğu içinde:

```text
feature: <yapılan işin kısa özeti>
- <değişiklik maddesi>
- <değişiklik maddesi>
- <değişiklik maddesi>
```
