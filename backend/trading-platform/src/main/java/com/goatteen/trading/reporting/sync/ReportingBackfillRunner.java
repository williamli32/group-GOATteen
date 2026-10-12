package com.goatteen.trading.reporting.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reporting.backfill-enabled", havingValue = "true")
public class ReportingBackfillRunner
        implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(
            ReportingBackfillRunner.class);

    private final OrderFactBackfillService orderFactBackfillService;

    private final TradeFactBackfillService tradeFactBackfillService;

    public ReportingBackfillRunner(
            OrderFactBackfillService orderFactBackfillService,
            TradeFactBackfillService tradeFactBackfillService) {

        this.orderFactBackfillService = orderFactBackfillService;

        this.tradeFactBackfillService = tradeFactBackfillService;
    }

    @Override
    public void run(
            ApplicationArguments args) {

        logger.info(
                "Starting historical reporting backfill");

        try {

            long orders = orderFactBackfillService.backfill();

            logger.info(
                    "Order reporting backfill completed. "
                            + "Orders synchronized: {}",
                    orders);

        } catch (Exception ex) {

            logger.error(
                    "Order reporting backfill failed",
                    ex);
        }

        try {

            long fills = tradeFactBackfillService.backfill();

            logger.info(
                    "Trade reporting backfill completed. "
                            + "Fills synchronized: {}",
                    fills);

        } catch (Exception ex) {

            logger.error(
                    "Trade reporting backfill failed",
                    ex);
        }

        logger.info(
                "Historical reporting backfill finished");
    }
}