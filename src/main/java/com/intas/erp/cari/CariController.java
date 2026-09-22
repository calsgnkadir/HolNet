package com.intas.erp.cari;

import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CariController {

  private final CariService cariService;

  public CariController(CariService cariService) {
    this.cariService = cariService;
  }

  @GetMapping("/cari")
  public String list(Model model) {
    model.addAttribute("customers", cariService.all());
    model.addAttribute("totalReceivable", cariService.totalReceivable());
    return "cari-list";
  }

  @GetMapping("/cari/yeni")
  public String newForm() {
    return "cari-new";
  }

  @PostMapping("/cari/yeni")
  public String create(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam("name") String name,
      @RequestParam(name = "city", required = false) String city,
      @RequestParam(name = "phone", required = false) String phone,
      @RequestParam(name = "openingBalance", required = false) String openingBalance,
      RedirectAttributes redirect) {
    try {
      Customer c = cariService.create(code, name, city, phone, parseMoney(openingBalance));
      redirect.addFlashAttribute("message", c.getName() + " carisi oluşturuldu.");
      return "redirect:/cari/" + c.getId();
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/cari/yeni";
    }
  }

  @GetMapping("/cari/{id}")
  public String detail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
    try {
      Customer customer = cariService.get(id);
      model.addAttribute("customer", customer);
      model.addAttribute("ledger", cariService.ledger(customer));
      return "cari-detail";
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
      return "redirect:/cari";
    }
  }

  @PostMapping("/cari/{id}/tahsilat")
  public String payment(
      @PathVariable Long id,
      @RequestParam("amount") String amount,
      @RequestParam(name = "note", required = false) String note,
      RedirectAttributes redirect) {
    try {
      cariService.recordPayment(id, parseMoney(amount), note);
      redirect.addFlashAttribute("message", "Tahsilat işlendi.");
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/cari/" + id;
  }

  /** "1.234,50" veya "1234.50" → BigDecimal; boş → null. */
  private static BigDecimal parseMoney(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return new BigDecimal(raw.trim().replace(".", "").replace(',', '.'));
  }
}
