package com.intas.erp.sale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Bir satıştan yazdırılabilir İrsaliye / Satış Faturası PDF'i üretir (OpenPDF).
 * Bu belge resmi e-Fatura DEĞİLDİR — iç sevk/satış belgesidir (GİB entegrasyonu
 * kapsam dışı). Türkçe karakterler için sistemdeki Arial gömülür; bulunamazsa
 * Cp1254 kodlamalı standart Helvetica'ya düşer.
 */
@Service
public class SalePdfService {

  private static final String COMPANY = "İNTAŞ SPOT ZÜCCACİYE SAN. VE TİC. LTD. ŞTİ.";
  private static final String COMPANY_SUB = "İSTOÇ Toptancılar Çarşısı — Bağcılar / İstanbul";
  private static final Color INK = new Color(0x1f, 0x29, 0x37);
  private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
  private static final Color LINE = new Color(0xe5, 0xe7, 0xeb);
  private static final Color HEADBG = new Color(0xf3, 0xf4, 0xf6);

  private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
  private final DecimalFormat money;

  public SalePdfService() {
    DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("tr", "TR"));
    this.money = new DecimalFormat("#,##0.00", s);
  }

  /** @param docTitle örn. "SATIŞ FATURASI" ya da "İRSALİYE". */
  public byte[] render(Sale sale, String docTitle) {
    BaseFont base = loadBaseFont();
    Font title = new Font(base, 18, Font.BOLD, INK);
    Font companyFont = new Font(base, 12, Font.BOLD, INK);
    Font subFont = new Font(base, 8, Font.NORMAL, MUTED);
    Font metaFont = new Font(base, 9, Font.NORMAL, INK);
    Font th = new Font(base, 8, Font.BOLD, INK);
    Font td = new Font(base, 9, Font.NORMAL, INK);
    Font tdMuted = new Font(base, 8, Font.NORMAL, MUTED);
    Font totalFont = new Font(base, 11, Font.BOLD, INK);
    Font footFont = new Font(base, 7, Font.NORMAL, MUTED);

    Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(doc, out);
      doc.open();

      // Başlık bloğu: firma solda, belge adı sağda.
      PdfPTable head = new PdfPTable(2);
      head.setWidthPercentage(100);
      head.setWidths(new int[] {60, 40});
      Phrase companyPhrase = new Phrase();
      companyPhrase.add(new com.lowagie.text.Chunk(COMPANY + "\n", companyFont));
      companyPhrase.add(new com.lowagie.text.Chunk(COMPANY_SUB, subFont));
      head.addCell(borderless(companyPhrase));
      PdfPCell titleCell = borderless(new Phrase(docTitle, title));
      titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      head.addCell(titleCell);
      doc.add(head);

      doc.add(spacer(10));

      // Meta: fiş no / tarih / cari
      String cari =
          sale.getCustomer() != null
              ? (sale.getCustomer().getCode() != null ? sale.getCustomer().getCode() + " · " : "")
                  + sale.getCustomer().getName()
              : (sale.getCustomerName() != null ? sale.getCustomerName() : "Muhtelif Müşteri");
      String tarih =
          sale.getCompletedAt() != null
              ? dateFormat.format(sale.getCompletedAt().atZone(ZoneId.systemDefault()))
              : "-";

      PdfPTable meta = new PdfPTable(2);
      meta.setWidthPercentage(100);
      meta.setWidths(new int[] {50, 50});
      meta.addCell(metaLine("Belge No: ", "#" + sale.getId(), th, metaFont));
      PdfPCell dateCell = metaLine("Tarih: ", tarih, th, metaFont);
      dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
      meta.addCell(dateCell);
      PdfPCell cariCell = metaLine("Sayın: ", cari, th, metaFont);
      cariCell.setColspan(2);
      cariCell.setPaddingTop(4);
      meta.addCell(cariCell);
      doc.add(meta);

      doc.add(spacer(10));

      // Kalem tablosu
      PdfPTable t = new PdfPTable(new float[] {12, 30, 8, 8, 8, 12, 8, 14});
      t.setWidthPercentage(100);
      headerCell(t, "Kod", th);
      headerCell(t, "Ürün", th);
      headerCellR(t, "Miktar", th);
      headerCell(t, "Birim", th);
      headerCellR(t, "Adet", th);
      headerCellR(t, "Birim Fiyat", th);
      headerCellR(t, "KDV%", th);
      headerCellR(t, "Tutar", th);

      for (SaleItem it : sale.getItems()) {
        bodyCell(t, it.getProductCode(), tdMuted, Element.ALIGN_LEFT);
        bodyCell(t, it.getProductName(), td, Element.ALIGN_LEFT);
        bodyCell(t, String.valueOf(it.getQuantity()), td, Element.ALIGN_RIGHT);
        bodyCell(t, it.getUnitLabel(), td, Element.ALIGN_LEFT);
        bodyCell(t, String.valueOf(it.getBaseQuantity()), td, Element.ALIGN_RIGHT);
        bodyCell(t, it.getUnitPrice() != null ? tl(it.getUnitPrice()) : "-", td, Element.ALIGN_RIGHT);
        bodyCell(
            t, it.getVatRate() != null ? "%" + strip(it.getVatRate()) : "-", td, Element.ALIGN_RIGHT);
        bodyCell(t, it.getLineTotal() != null ? tl(it.getLineTotal()) : "-", td, Element.ALIGN_RIGHT);
      }
      doc.add(t);

      doc.add(spacer(8));

      // Toplamlar (sağa hizalı küçük tablo)
      PdfPTable tot = new PdfPTable(new float[] {70, 30});
      tot.setWidthPercentage(100);
      totalRow(tot, "Ara Toplam (KDV hariç)", tl(sale.getTotal()), metaFont, metaFont);
      totalRow(tot, "Toplam KDV", tl(sale.getTotalVat()), metaFont, metaFont);
      totalRow(tot, "GENEL TOPLAM", tl(sale.getTotalGross()), totalFont, totalFont);
      doc.add(tot);

      doc.add(spacer(24));

      // İmza satırları
      PdfPTable sign = new PdfPTable(2);
      sign.setWidthPercentage(100);
      sign.setWidths(new int[] {50, 50});
      sign.addCell(signCell("Teslim Eden", metaFont));
      sign.addCell(signCell("Teslim Alan", metaFont));
      doc.add(sign);

      doc.add(spacer(10));
      Paragraph foot =
          new Paragraph(
              "Bu belge resmi e-Fatura değildir; işletme içi sevk/satış belgesidir.", footFont);
      foot.setAlignment(Element.ALIGN_CENTER);
      doc.add(foot);

      doc.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("PDF üretilemedi: " + e.getMessage(), e);
    }
  }

  private String tl(BigDecimal v) {
    return money.format(v == null ? BigDecimal.ZERO : v) + " TL";
  }

  /** "20.00" → "20", "18.50" → "18,5" gibi sade KDV oranı. */
  private String strip(BigDecimal v) {
    return money.format(v).replaceAll(",00$", "").replaceAll("0$", "").replaceAll(",$", "");
  }

  private static BaseFont loadBaseFont() {
    try {
      File arial = new File("C:/Windows/Fonts/arial.ttf");
      if (arial.exists()) {
        return BaseFont.createFont(
            arial.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
      }
    } catch (Exception ignored) {
      // düş: Cp1254 Helvetica
    }
    try {
      return BaseFont.createFont(BaseFont.HELVETICA, "Cp1254", BaseFont.NOT_EMBEDDED);
    } catch (Exception e) {
      throw new IllegalStateException("Yazı tipi yüklenemedi.", e);
    }
  }

  // --- küçük hücre yardımcıları ---

  private static PdfPCell borderless(Phrase p) {
    PdfPCell c = new PdfPCell(p);
    c.setBorder(0);
    return c;
  }

  private PdfPCell metaLine(String label, String value, Font labelFont, Font valueFont) {
    Phrase p = new Phrase();
    p.add(new com.lowagie.text.Chunk(label, labelFont));
    p.add(new com.lowagie.text.Chunk(value, valueFont));
    return borderless(p);
  }

  private void headerCell(PdfPTable t, String text, Font f) {
    PdfPCell c = new PdfPCell(new Phrase(text, f));
    c.setBackgroundColor(HEADBG);
    c.setBorderColor(LINE);
    c.setPadding(5);
    t.addCell(c);
  }

  private void headerCellR(PdfPTable t, String text, Font f) {
    PdfPCell c = new PdfPCell(new Phrase(text, f));
    c.setBackgroundColor(HEADBG);
    c.setBorderColor(LINE);
    c.setPadding(5);
    c.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(c);
  }

  private void bodyCell(PdfPTable t, String text, Font f, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", f));
    c.setBorderColor(LINE);
    c.setPadding(5);
    c.setHorizontalAlignment(align);
    t.addCell(c);
  }

  private void totalRow(PdfPTable t, String label, String value, Font lf, Font vf) {
    PdfPCell l = new PdfPCell(new Phrase(label, lf));
    l.setBorder(0);
    l.setHorizontalAlignment(Element.ALIGN_RIGHT);
    l.setPadding(4);
    PdfPCell v = new PdfPCell(new Phrase(value, vf));
    v.setBorder(0);
    v.setHorizontalAlignment(Element.ALIGN_RIGHT);
    v.setPadding(4);
    t.addCell(l);
    t.addCell(v);
  }

  private PdfPCell signCell(String label, Font f) {
    PdfPCell c = new PdfPCell(new Phrase("\n\n\n_____________________\n" + label, f));
    c.setBorder(0);
    c.setHorizontalAlignment(Element.ALIGN_CENTER);
    return c;
  }

  private static Paragraph spacer(float h) {
    Paragraph p = new Paragraph(" ");
    p.setLeading(h);
    return p;
  }
}
