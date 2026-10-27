package com.goatteen.trading.watchlist;

import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {
    private final WatchlistService watchlists;
    private final CurrentUserService currentUser;

    public WatchlistController(WatchlistService watchlists, CurrentUserService currentUser) {
        this.watchlists = watchlists;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<MarketInstrumentResponse> list() {
        return watchlists.list(currentUser.getCurrentUserId());
    }

    @PutMapping("/{instrumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable Long instrumentId) {
        watchlists.add(currentUser.getCurrentUserId(), instrumentId);
    }

    @DeleteMapping("/{instrumentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long instrumentId) {
        watchlists.remove(currentUser.getCurrentUserId(), instrumentId);
    }
}
