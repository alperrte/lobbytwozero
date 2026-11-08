# Gerçek katalog görsel test örnekleri

`hardware-visual-products.json`, 2026-11-08 tarihinde local `hardware_db` içinden
salt okunur SELECT ile alınan 10 gerçek üründür. Kaynak: PC Part Dataset commit
`c52a04ca9465c83997ed335f7767b09a2005dd26`; fiyat veya kişisel veri içermez.
UUID, model, marka ve teknik alanlar korunmuştur. CPU AMD/Intel, GPU NVIDIA/AMD/Intel,
anakart, DDR5 RAM, M.2 SSD, SATA SSD ve HDD örnekleri vardır.

Yalnız Vitest ve Playwright testleri bu snapshot'ı import eder. Production bileşeni
ve servisleri fixture'ı kullanmaz. Playwright bu kayıtları tarayıcıda API replay ile
sunar; bu, canlı Auth/Gateway uçtan uca kabulü olarak raporlanmaz. Ürün fotoğrafı yoktur.
