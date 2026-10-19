package com.goatteen.trading.alerts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/price-alerts")
public class PriceAlertController {
    private final PriceAlertService service;
    public PriceAlertController(PriceAlertService service) { this.service=service; }

    @GetMapping public List<PriceAlertService.Alert> list() { return service.alerts(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PriceAlertService.Alert create(@RequestBody PriceAlertService.Change body) {return service.create(body);}
    @PutMapping("/{id}")
    public PriceAlertService.Alert update(@PathVariable long id,@RequestBody PriceAlertService.Change body) {
        return service.update(id,body);
    }
    public record Enabled(boolean enabled) {}
    @PatchMapping("/{id}/enabled")
    public PriceAlertService.Alert enable(@PathVariable long id,@RequestBody Enabled body) {
        return service.setEnabled(id,body.enabled());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {service.delete(id);}
    @GetMapping("/notifications")
    public List<PriceAlertService.Notice> notifications() {return service.notifications();}
    @DeleteMapping("/notifications") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAllNotifications() { service.clearAllNotifications(); }

    @DeleteMapping("/notifications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearNotification(@PathVariable long id) { service.clearNotification(id); }

    // Retain the old endpoint for backward compatibility with already deployed clients.
    @PatchMapping("/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id) {service.markRead(id);}
}
