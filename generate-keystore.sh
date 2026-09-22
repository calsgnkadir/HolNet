#!/usr/bin/env bash
# İntaş ERP — yerel HTTPS için kendinden imzalı sertifika üretir.
# Telefon kamerasıyla barkod okutmak için sunucu HTTPS olmalı (tarayıcı kamerayı
# yalnızca localhost veya https'te açar). Bu betik özel anahtarı YEREL üretir;
# üretilen keystore.p12 repoya konmaz (.gitignore).
#
# Kullanım:
#   ./generate-keystore.sh                # sadece localhost
#   ./generate-keystore.sh 192.168.1.110  # telefon erişimi için LAN IP'yi ekle
set -euo pipefail

LAN_IP="${1:-}"
OUT="src/main/resources/keystore.p12"
SAN="dns:localhost,ip:127.0.0.1"
if [ -n "$LAN_IP" ]; then
  SAN="$SAN,ip:$LAN_IP"
fi

keytool -genkeypair -alias intaserp -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -keystore "$OUT" -storepass changeit \
  -dname "CN=intas-erp, O=Intas Spot, L=Istanbul, C=TR" \
  -ext "SAN=$SAN"

echo "Oluşturuldu: $OUT  (SAN=$SAN)"
echo "Parola: changeit  (application.properties ile aynı olmalı)"
