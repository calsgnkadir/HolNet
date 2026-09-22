/*
 * Basit barkod okuyucu sarmalayıcı (html5-qrcode üstünde).
 * ErpScanner.open(inputId, { submitFormId }) — kamerayı açar, okunan barkodu
 * verilen input'a yazar; submitFormId verilirse o formu gönderir (satışta
 * "okut → 1 adet ekle" akışı).
 *
 * NOT: Tarayıcı kamerası yalnızca localhost veya HTTPS'te açılır. Telefonda
 * http ile LAN üzerinden erişilirse kamera engellenir — o durumda USB/Bluetooth
 * barkod okuyucu (klavye gibi davranır) kod alanına doğrudan yazıp Enter'lar.
 */
(function () {
  var scanner = null;

  function libReady() {
    return typeof Html5Qrcode !== "undefined";
  }

  function supportedFormats() {
    if (typeof Html5QrcodeSupportedFormats === "undefined") {
      return undefined;
    }
    return [
      Html5QrcodeSupportedFormats.EAN_13,
      Html5QrcodeSupportedFormats.EAN_8,
      Html5QrcodeSupportedFormats.UPC_A,
      Html5QrcodeSupportedFormats.UPC_E,
      Html5QrcodeSupportedFormats.CODE_128,
      Html5QrcodeSupportedFormats.CODE_39,
      Html5QrcodeSupportedFormats.QR_CODE
    ];
  }

  function buildOverlay() {
    var overlay = document.createElement("div");
    overlay.className = "scan-overlay";
    overlay.innerHTML =
      '<div class="scan-box">' +
      '  <div class="scan-head">' +
      "    <span>Barkodu kameraya gösterin</span>" +
      '    <button type="button" class="btn ghost" id="scan-close">Kapat</button>' +
      "  </div>" +
      '  <div id="erp-reader"></div>' +
      '  <div class="scan-msg" id="scan-msg">Kamera başlatılıyor…</div>' +
      "</div>";
    document.body.appendChild(overlay);
    return overlay;
  }

  function teardown(overlay) {
    var remove = function () {
      if (overlay && overlay.parentNode) {
        overlay.parentNode.removeChild(overlay);
      }
    };
    if (scanner) {
      scanner
        .stop()
        .then(function () {
          scanner.clear();
          scanner = null;
          remove();
        })
        .catch(remove);
    } else {
      remove();
    }
  }

  window.ErpScanner = {
    open: function (inputId, opts) {
      opts = opts || {};
      if (!libReady()) {
        alert("Barkod kütüphanesi yüklenemedi. İnternet bağlantısını kontrol edin.");
        return;
      }

      var overlay = buildOverlay();
      var msg = overlay.querySelector("#scan-msg");
      overlay.querySelector("#scan-close").addEventListener("click", function () {
        teardown(overlay);
      });

      var formats = supportedFormats();
      scanner = new Html5Qrcode("erp-reader", formats ? { formatsToSupport: formats } : undefined);

      scanner
        .start(
          { facingMode: "environment" },
          { fps: 10, qrbox: { width: 260, height: 160 } },
          function onDecode(decodedText) {
            var input = document.getElementById(inputId);
            if (input) {
              input.value = decodedText;
            }
            if (navigator.vibrate) {
              navigator.vibrate(80);
            }
            teardown(overlay);
            if (opts.submitFormId) {
              var form = document.getElementById(opts.submitFormId);
              if (form) {
                form.submit();
              }
            }
          },
          function onScanError() {
            /* kare başına decode hatalarını yok say */
          }
        )
        .then(function () {
          msg.textContent = "Kamera hazır — barkodu çerçeveye getirin.";
        })
        .catch(function (err) {
          msg.textContent =
            "Kamera açılamadı: " +
            err +
            " — Telefonda HTTPS gerekir. Bilgisayarda USB barkod okuyucu doğrudan çalışır (kod alanına okutun).";
        });
    }
  };
})();
