/*
  İntaş ERP — Go Plus veritabanı için SALT-OKUNUR kullanıcı
  ========================================================

  NEDEN: "sa" şifresini SIFIRLAMAYIN. Go Plus programı SQL Server'a büyük ihtimalle bu
  hesapla bağlanıyor; şifresi değişirse Go Plus açılmaz, fatura kesilemez. Bu betik sa'ya ve
  Go Plus'ın bağlantısına dokunmadan, yalnızca OKUYABİLEN ayrı bir kullanıcı açar. İntaş ERP'nin
  ileride Go Plus'tan otomatik veri çekmesi de bu kullanıcıyla yapılır.

  NASIL:
    1. MERKEZ1 bilgisayarında SSMS'i açın.
    2. Server name: MERKEZ1\GOPLUS   Authentication: Windows Authentication   → Connect
    3. Sol panelde Databases altında Go Plus veritabanının adını bulun ve aşağıdaki
       [GOPLUS_VERITABANI] yazan yeri o adla değiştirin.
    4. <GÜÇLÜ-BİR-ŞİFRE> yerine en az 12 karakterlik bir şifre yazın, bir yere not edin.
    5. Execute (F5).

  NE YAPAR / YAPMAZ:
    + db_datareader  → tüm tabloları OKUYABİLİR (SELECT)
    + db_denydatawriter → yazma/silme/güncelleme AÇIKÇA yasak
    - Başka veritabanına, sunucu ayarlarına, "sa"ya erişimi YOK

  Geri almak isterseniz en alttaki "KALDIR" bölümünü çalıştırın.
*/

USE [master];
GO
CREATE LOGIN [intas_okur]
    WITH PASSWORD = N'<GÜÇLÜ-BİR-ŞİFRE>',
         CHECK_POLICY = ON,
         DEFAULT_DATABASE = [GOPLUS_VERITABANI];
GO

USE [GOPLUS_VERITABANI];
GO
CREATE USER [intas_okur] FOR LOGIN [intas_okur];
ALTER ROLE db_datareader     ADD MEMBER [intas_okur];
ALTER ROLE db_denydatawriter ADD MEMBER [intas_okur];
GO

-- Kontrol: yetkiler (db_datareader + db_denydatawriter görünmeli)
SELECT r.name AS rol
FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
WHERE u.name = N'intas_okur';
GO

/* ---- KALDIR (gerekirse) ----
USE [GOPLUS_VERITABANI];  DROP USER  [intas_okur];
USE [master];             DROP LOGIN [intas_okur];
*/
