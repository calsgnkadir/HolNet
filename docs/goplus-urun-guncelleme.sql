/*
  İntaş ERP — Go Plus'tan fiyat / barkod / koli verisi
  ====================================================

  Kim çalıştırır: MERKEZ1 bilgisayarında SSMS'i açıp "MERKEZ1\GOPLUS" sunucusuna Windows
                  Authentication ile bağlanan kişi (ya da bayi). Çalıştırmadan önce SSMS'in üst
                  çubuğundaki veritabanı listesinden Go Plus veritabanını seçin.
  Ne yapar      : SADECE OKUR (SELECT, WITH (NOLOCK) — Go Plus çalışırken kilitlemez).
                  Go Plus verisinde hiçbir şeyi değiştirmez.
  Çıktı         : İntaş ERP şablonuyla birebir aynı sütunlar —
                  Kodu | Barkod | Koli Barkodu | Koli İçi Adet | Satış Fiyatı | Alış Fiyatı | KDV
  Kaydetme      : Sonuç tablosunda sağ tık > "Copy with Headers" → boş bir Excel'e yapıştır →
                  .xlsx olarak kaydet. Sonra İntaş ERP > Ürünler > "Fiyat / Barkod / Koli Yükle".

  Firma no 127 ("(127) İNTAŞ SPOT"). Başka firma için tüm "LG_127_" öneklerini değiştirin.

  NOT: Tablo ve alan adları Logo'nun standart şemasına göre yazıldı. Sürüme göre küçük
  farklar olabilir — bayi ilk çalıştırmada kontrol etmeli. Hızlı doğrulama:
    KDR-YP703G satırında "Koli İçi Adet" = 60 çıkmalı (Go Plus'ta 1 KOLİ = 60 ADET).
    Eğer 0,0166... gibi ters bir değer çıkarsa KOLI bloğundaki CONVFACT1/CONVFACT2 yer değiştirin.
  Fiyatlar KDV HARİÇ verilir: fiyat kartı "KDV dahil" girildiyse sorgu KDV'yi ayırır.
*/

SELECT
    I.CODE          AS [Kodu],
    AB.BARCODE      AS [Barkod],
    KB.BARCODE      AS [Koli Barkodu],
    KOLI.ADET       AS [Koli İçi Adet],
    SF.NETFIYAT     AS [Satış Fiyatı],
    AF.NETFIYAT     AS [Alış Fiyatı],
    I.VAT           AS [KDV]
FROM LG_127_ITEMS I WITH (NOLOCK)

-- Ana birim (ADET) satırı
OUTER APPLY (
    SELECT TOP 1 U.LOGICALREF AS REF
    FROM LG_127_UNITSETL U WITH (NOLOCK)
    WHERE U.UNITSETREF = I.UNITSETREF AND U.MAINUNIT = 1
) ANA

-- Koli: ana birim olmayan ilk birim ve çevrim katsayısı (1 KOLİ = N ADET)
OUTER APPLY (
    SELECT TOP 1
        A.UNITLINEREF AS REF,
        CAST(ROUND(A.CONVFACT2 / NULLIF(A.CONVFACT1, 0), 0) AS INT) AS ADET
    FROM LG_127_ITMUNITA A WITH (NOLOCK)
    WHERE A.ITEMREF = I.LOGICALREF AND A.UNITLINEREF <> ANA.REF
    ORDER BY A.LINENR
) KOLI

-- Birimli barkodlar: adetin ve kolinin ayrı barkodu
OUTER APPLY (
    SELECT TOP 1 B.BARCODE FROM LG_127_UNITBARCODE B WITH (NOLOCK)
    WHERE B.ITEMREF = I.LOGICALREF AND B.UNITLINEREF = ANA.REF
    ORDER BY B.LINENR
) AB
OUTER APPLY (
    SELECT TOP 1 B.BARCODE FROM LG_127_UNITBARCODE B WITH (NOLOCK)
    WHERE B.ITEMREF = I.LOGICALREF AND B.UNITLINEREF = KOLI.REF
    ORDER BY B.LINENR
) KB

-- Güncel SATIŞ fiyatı (PTYPE = 2), adet birimi için, KDV hariç
OUTER APPLY (
    SELECT TOP 1
        CASE WHEN P.INCVAT = 1 THEN P.PRICE / (1 + I.VAT / 100.0) ELSE P.PRICE END AS NETFIYAT
    FROM LG_127_PRCLIST P WITH (NOLOCK)
    WHERE P.CARDREF = I.LOGICALREF AND P.PTYPE = 2 AND P.ACTIVE = 0
      AND (P.UOMREF = ANA.REF OR P.UOMREF = 0)
    ORDER BY P.BEGDATE DESC, P.LOGICALREF DESC
) SF

-- Güncel ALIŞ fiyatı (PTYPE = 1), adet birimi için, KDV hariç
OUTER APPLY (
    SELECT TOP 1
        CASE WHEN P.INCVAT = 1 THEN P.PRICE / (1 + I.VAT / 100.0) ELSE P.PRICE END AS NETFIYAT
    FROM LG_127_PRCLIST P WITH (NOLOCK)
    WHERE P.CARDREF = I.LOGICALREF AND P.PTYPE = 1 AND P.ACTIVE = 0
      AND (P.UOMREF = ANA.REF OR P.UOMREF = 0)
    ORDER BY P.BEGDATE DESC, P.LOGICALREF DESC
) AF

WHERE I.ACTIVE = 0      -- kullanımdaki kartlar (Logo'da 0 = kullanımda)
  AND I.CARDTYPE = 1    -- (TM) Ticari Mal
ORDER BY I.CODE;
