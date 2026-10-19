package com.goatteen.trading.fx;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicative conversion shown on the Angular order ticket. */
@RestController
@RequestMapping("/api/fx")
public class FxRateController {
    private final FxRateService rates;

    public FxRateController(FxRateService rates) {
        this.rates = rates;
    }

    @GetMapping("/rate")
    public FxRateService.FxQuote getRate(@RequestParam String from,
                                          @RequestParam String to) {
        return rates.quote(from, to);
    }
}
