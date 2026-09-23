package com.intas.erp.slip;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StockSlipController {

  private final StockSlipService slipService;

  public StockSlipController(StockSlipService slipService) {
    this.slipService = slipService;
  }

  @GetMapping("/fisler")
  public String list(Model model) {
    model.addAttribute("slips", slipService.completed());
    return "slips";
  }

  @GetMapping("/fisler/yeni")
  public String draft(Model model) {
    model.addAttribute("slip", slipService.currentDraft());
    return "slip";
  }

  @PostMapping("/fisler/ekle")
  public String addLine(
      @RequestParam("code") String code,
      @RequestParam(name = "quantity", defaultValue = "1") int quantity,
      @RequestParam(name = "birim", defaultValue = "ADET") String birim,
      RedirectAttributes redirect) {
    try {
      slipService.addLine(code, quantity, "KOLI".equals(birim));
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/fisler/yeni";
  }

  @PostMapping("/fisler/satir/sil")
  public String removeLine(@RequestParam("lineId") Long lineId) {
    slipService.removeLine(lineId);
    return "redirect:/fisler/yeni";
  }

  @PostMapping("/fisler/temizle")
  public String clear() {
    slipService.clearDraft();
    return "redirect:/fisler/yeni";
  }

  @PostMapping("/fisler/kaydet")
  public String complete(
      @RequestParam(name = "yon", defaultValue = "GIRIS") String yon,
      @RequestParam(name = "documentNo", required = false) String documentNo,
      @RequestParam(name = "description", required = false) String description,
      RedirectAttributes redirect) {
    try {
      StockSlip saved = slipService.complete("GIRIS".equals(yon), documentNo, description);
      redirect.addFlashAttribute("message", "Fiş kaydedildi — stok güncellendi.");
      return "redirect:/fisler/" + saved.getId();
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/fisler/yeni";
    }
  }

  @GetMapping("/fisler/{id}")
  public String detail(@PathVariable Long id, Model model) {
    model.addAttribute("slip", slipService.get(id));
    return "slip-detail";
  }
}
