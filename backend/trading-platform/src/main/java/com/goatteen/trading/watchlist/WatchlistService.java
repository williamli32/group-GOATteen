package com.goatteen.trading.watchlist;

import com.goatteen.trading.instrument.InstrumentRepository;
import com.goatteen.trading.marketdata.dto.MarketInstrumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WatchlistService {
    private final WatchlistRepository watchlists;
    private final InstrumentRepository instruments;

    public WatchlistService(WatchlistRepository watchlists, InstrumentRepository instruments) {
        this.watchlists = watchlists;
        this.instruments = instruments;
    }

    @Transactional(readOnly = true)
    public List<MarketInstrumentResponse> list(Long userId) {
        return watchlists.findByUserId(userId);
    }

    @Transactional
    public void add(Long userId, Long instrumentId) {
        requireInstrumentId(instrumentId);
        var instrument = instruments.findById(instrumentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        if (!instrument.isTradable()) {
            throw new IllegalArgumentException("This instrument is not currently tradable");
        }
        watchlists.addIfAbsent(userId, instrumentId);
    }

    @Transactional
    public void remove(Long userId, Long instrumentId) {
        requireInstrumentId(instrumentId);
        // An absent entry is a successful, idempotent removal. Never look up
        // another user's entries to decide whether this request should succeed.
        watchlists.remove(userId, instrumentId);
    }

    private void requireInstrumentId(Long instrumentId) {
        if (instrumentId == null || instrumentId <= 0) {
            throw new IllegalArgumentException("Select a valid instrument");
        }
    }
}
