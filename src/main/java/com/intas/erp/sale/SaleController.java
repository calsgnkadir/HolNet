package com.intas.erp.sale;

import com.intas.erp.cari.CariService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SaleController {

  private final SaleService saleService;
  private final CariService cariService;
  private final SalePdfService pdfService;

  public SaleController(
      SaleService saleService, CariService cariService, SalePdfService pdfService) {
    this.saleService = saleService;
    this.cariService = cariService;
    this.pdfService = pdfService;
  }

  @GetMapping("/satis")
  public String draft(Model model) {
    model.addAttribute("sale", saleService.currentDraft());
    model.addAttribute("customers", cariService.all());
    return "sale";
  }

  @PostMapping("/satis/ekle")
  public String addLine(
      @RequestParam("code") String code,
      @RequestParam(name = "quantity", defaultValue = "1") int quantity,
      @RequestParam(name = "birim", defaultValue = "ADET") String birim,
      RedirectAttributes redirect) {
    try {
      saleService.addLine(code, quantity, "KOLI".equals(birim));
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/satis";
  }

  @PostMapping("/satis/satir/sil")
  public String removeLine(@RequestParam("itemId") Long itemId) {
    saleService.removeLine(itemId);
    return "redirect:/satis";
  }

  @PostMapping("/satis/temizle")
  public String clear() {
    saleService.clearDraft();
    return "redirect:/satis";
  }

  @PostMapping("/satis/tamamla")
  public String complete(
      @RequestParam(name = "customerId", required = false) Long customerId,
      @RequestParam(name = "customerName", required = false) String customerName,
      RedirectAttributes redirect) {
    try {
      Sale completed = saleService.complete(customerId, customerName);
      return "redirect:/satis/" + completed.getId() + "/fis";
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/satis";
    }
  }

  @GetMapping("/satislar")
  public String sales(Model model) {
    model.addAttribute("sales", saleService.completedSales());
    return "sales-list";
  }

  @GetMapping("/satis/{id}/fis")
  public String receipt(@PathVariable Long id, Model model) {
    model.addAttribute("sale", saleService.get(id));
    return "receipt";
  }

  @GetMapping("/satis/{id}/pdf")
  public ResponseEntity<byte[]> pdf(
      @PathVariable Long id,
      @RequestParam(name = "tip", defaultValue = "fatura") String tip) {
    Sale sale = saleService.get(id);
    boolean irsaliye = "irsaliye".equalsIgnoreCase(tip);
    String title = irsaliye ? "İRSALİYE" : "SATIŞ FATURASI";
    byte[] pdf = pdfService.render(sale, title);

    String filename = (irsaliye ? "irsaliye" : "fatura") + "-" + id + ".pdf";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
    return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
  }
}
