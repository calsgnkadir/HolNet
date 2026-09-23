package com.intas.erp.purchase;

import com.intas.erp.cari.CariService;
import com.intas.erp.common.Money;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PurchaseController {

  private final PurchaseService purchaseService;
  private final CariService cariService;

  public PurchaseController(PurchaseService purchaseService, CariService cariService) {
    this.purchaseService = purchaseService;
    this.cariService = cariService;
  }

  @GetMapping("/alislar")
  public String list(Model model) {
    model.addAttribute("purchases", purchaseService.completed());
    return "purchases";
  }

  @GetMapping("/alis")
  public String draft(Model model) {
    model.addAttribute("purchase", purchaseService.currentDraft());
    model.addAttribute("customers", cariService.all());
    return "purchase";
  }

  @PostMapping("/alis/ekle")
  public String addLine(
      @RequestParam("code") String code,
      @RequestParam(name = "quantity", defaultValue = "1") int quantity,
      @RequestParam(name = "birim", defaultValue = "ADET") String birim,
      @RequestParam(name = "unitPrice", required = false) String unitPrice,
      RedirectAttributes redirect) {
    try {
      purchaseService.addLine(code, quantity, "KOLI".equals(birim), Money.parse(unitPrice));
    } catch (NumberFormatException e) {
      redirect.addFlashAttribute("error", "Fiyat sayı olmalı (örn. 12,50).");
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/alis";
  }

  @PostMapping("/alis/satir/sil")
  public String removeLine(@RequestParam("lineId") Long lineId) {
    purchaseService.removeLine(lineId);
    return "redirect:/alis";
  }

  @PostMapping("/alis/temizle")
  public String clear() {
    purchaseService.clearDraft();
    return "redirect:/alis";
  }

  @PostMapping("/alis/tamamla")
  public String complete(
      @RequestParam(name = "supplierId", required = false) Long supplierId,
      @RequestParam(name = "documentNo", required = false) String documentNo,
      RedirectAttributes redirect) {
    try {
      Purchase saved = purchaseService.complete(supplierId, documentNo);
      redirect.addFlashAttribute("message", "Alış faturası kaydedildi — stok ve cari güncellendi.");
      return "redirect:/alis/" + saved.getId();
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/alis";
    }
  }

  @GetMapping("/alis/{id}")
  public String detail(@PathVariable Long id, Model model) {
    model.addAttribute("purchase", purchaseService.get(id));
    return "purchase-detail";
  }
}
