package com.goatteen.trading.reporting.sync;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.client.Client;
import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.execution.FillRepository;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.order.Order;
import com.goatteen.trading.reporting.data.TradeFact;
import com.goatteen.trading.reporting.data.TradeFactRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TradeFactBackfillService {

    private static final Logger logger = LoggerFactory.getLogger(
            TradeFactBackfillService.class);

    private static final int BATCH_SIZE = 100;

    private final FillRepository fillRepository;
    private final TradeFactRepository tradeFactRepository;

    public TradeFactBackfillService(
            FillRepository fillRepository,
            TradeFactRepository tradeFactRepository) {

        this.fillRepository = fillRepository;
        this.tradeFactRepository = tradeFactRepository;
    }

    public long backfill() {

        long synchronizedFills = 0;
        int pageNumber = 0;

        List<Long> failedFillIds = new ArrayList<>();

        Page<Fill> page;

        do {

            page = fillRepository
                    .findAllForReportingBackfill(
                            PageRequest.of(
                                    pageNumber,
                                    BATCH_SIZE,
                                    Sort.by(
                                            Sort.Direction.ASC,
                                            "id")));

            for (Fill fill : page.getContent()) {

                try {

                    /*
                     * Backfill is idempotent.
                     * Do not recreate a TradeFact that
                     * already exists.
                     */
                    if (tradeFactRepository
                            .findByFillId(fill.getId())
                            .isPresent()) {

                        continue;
                    }

                    createTradeFact(fill);

                    synchronizedFills++;

                } catch (Exception ex) {

                    failedFillIds.add(
                            fill.getId());

                    logger.error(
                            "Reporting trade backfill failed for fill {}",
                            fill.getId(),
                            ex);
                }
            }

            pageNumber++;

        } while (page.hasNext());

        if (!failedFillIds.isEmpty()) {

            throw new IllegalStateException(
                    "Trade reporting backfill incomplete. "
                            + "Failed fill IDs: "
                            + failedFillIds
                            + ". Successfully synchronized: "
                            + synchronizedFills);
        }

        return synchronizedFills;
    }

    private void createTradeFact(Fill fill) {

        Order order = fill.getOrder();

        Account account = order.getAccount();

        Client client = account.getClient();

        Instrument instrument = order.getInstrument();

        Quote quote = fill.getQuote();

        BigDecimal tradeValue = fill.getFillPrice()
                .multiply(
                        fill.getFillQuantity());

        TradeFact tradeFact = new TradeFact(
                fill.getId(),
                order.getId(),

                order.getSubmittedAt(),
                order.getAcceptedAt(),
                fill.getExecutedAt(),

                account.getId(),
                client.getId(),

                client.getFirstName(),
                client.getLastName(),

                instrument.getId(),
                instrument.getSymbol(),
                instrument.getInstrumentClass()
                        .toString(),
                instrument.getName(),
                instrument.getCurrency(),

                order.getSide()
                        .toString(),

                order.getQuantity(),
                fill.getFillPrice(),
                fill.getFillQuantity(),
                tradeValue,

                order.getStatus()
                        .toString(),

                quote.getBidPrice(),
                quote.getAskPrice(),
                quote.getQuotedAt());

        tradeFactRepository.save(
                tradeFact);

        logger.info(
                "Backfilled reporting TradeFact for fill_id={}",
                fill.getId());
    }
}