# İntaş ERP

Toptancı bir züccaciye/hırdavat işletmesi için geliştirilen hafif **stok–satış–cari** yönetim aracı. Logo GO Plus'ın günlük kullanılan çekirdek akışını (malzeme, birim seti, satış, otomatik stok düşme, cari hesap, irsaliye/fatura) sadeleştirilmiş biçimde uygular.

> Gerçek bir işletmede (İntaş Spot, İSTOÇ / İstanbul) kullanılmak üzere yazıldı; aynı zamanda bir portföy projesidir.

## Ne yapar

- **Ürünler / Stok** — Go Plus Excel dökümünü (.xls/.xlsx) içe aktarır (7.500+ kalem), kod/ad üzerinde arama ve sayfalama.
- **Birim seti (koli ↔ adet)** — "1 KOLİ = 60 ADET" gibi çevrim; satışta koli seçilince stoktan `miktar × katsayı` düşer. İşin kalbi.
- **Satış + otomatik stok düşme** — kalem kalem satış girilir, tamamlanınca stok otomatik güncellenir, yazdırılabilir fiş çıkar.
- **KDV** — satır bazında oran, ara toplam / KDV / genel toplam.
- **Cari hesap** — müşteri kartları, **Borç/Alacak bakiye**, hesap ekstresi; satış cariyi borçlandırır, tahsilat düşer. Tek giriş noktasından (`CariService.apply`) bakiye + defter kaydı.
- **İrsaliye / Satış Faturası PDF** — OpenPDF ile A4 belge (kalemler, KDV, toplamlar, imza alanları).
- **Barkod** — telefon kamerası (html5-qrcode, HTTPS gerektirir) veya USB/Bluetooth barkod okuyucu (kod alanına doğrudan yazar).

## Teknoloji

| Katman | Seçim |
|---|---|
| Dil / Runtime | Java 21 |
| Çatı | Spring Boot 4.1, Spring MVC, Spring Data JPA (Hibernate 7) |
| Görünüm | Thymeleaf + sade el yazımı CSS (bağımlılıksız) |
| Veritabanı | H2 (dosya modu, dev) — MySQL'e geçiş tek konfig bloğu |
| Excel | Apache POI |
| PDF | OpenPDF |
| Barkod | html5-qrcode (istemci) |

## Kapsam kararları

- **Go Plus'ın klonu değildir.** 40+ sekme yerine işletmenin her gün kullandığı çekirdek akışa odaklanır.
- **Resmi e-Fatura/GİB entegrasyonu yoktur** — regüle bir alan. Üretilen PDF işletme içi sevk/satış belgesidir; resmi fatura yine GİB üzerinden kesilir.

## Güvenlik notları (bilinçli tercihler)

- **Aramalar prepared statement** — JPA parametre bağlama, SQL'e string birleştirme yok.
- **Özel anahtar repoda değil** — TLS keystore `.gitignore`'da; her makinede `generate-keystore.sh` ile yerelde üretilir.
- **HTTPS** — tarayıcı kamerası yalnızca localhost/https'te çalıştığı için self-signed sertifika ile sunulur.
- `open-in-view=false`, girdi doğrulama, katmanlı (controller → service → repository) yapı.

## Çalıştırma

Gereksinim: **JDK 21**. Maven wrapper gömülü (`mvnw`).

```bash
# 1) HTTPS sertifikası üret (telefon erişimi için LAN IP'yi ekle)
./generate-keystore.sh              # sadece bilgisayar
./generate-keystore.sh 192.168.1.110  # telefondan barkod için

# 2) Çalıştır
./mvnw spring-boot:run
```

- Uygulama: **https://localhost:8443**
- Telefon (aynı Wi-Fi): **https://<bilgisayar-ip>:8443** — self-signed uyarısını kabul et.
- H2 konsolu: `/h2`

Ürünleri yüklemek için **Ürünler → Excel'den İçe Aktar** ile Go Plus stok dökümünü verin.

## Ekranlar

`/urunler` · `/satis` · `/satislar` · `/cari` — üst menüden gezilir.
