package com.smartwatch.watchlist.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.entity.MarketObservation;
import com.smartwatch.marketdata.model.Quote;
import com.smartwatch.marketdata.provider.MockMarketDataProvider;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.marketdata.repository.MarketObservationRepository;
import com.smartwatch.marketdata.service.MarketDataService;
import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.watchlist.entity.Watchlist;
import com.smartwatch.watchlist.entity.WatchlistCheck;
import com.smartwatch.watchlist.entity.WatchlistCheckItem;
import com.smartwatch.watchlist.repository.WatchlistCheckItemRepository;
import com.smartwatch.watchlist.repository.WatchlistCheckRepository;
import com.smartwatch.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchlistChangeIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "strong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private MarketObservationRepository observationRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistCheckRepository checkRepository;

    @Autowired
    private WatchlistCheckItemRepository checkItemRepository;

    @Autowired
    private MarketDataService marketDataService;

    @Test
    void reportsNoMarketMoveBeforeTheFirstCheck() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        addItem(ada, watchlistId, instrument("RELIANCE", "Reliance Industries").getId());

        long observations = observationRepository.count();
        long checks = checkRepository.count();

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(nullValue()))
                .andExpect(jsonPath("$.firstCheck").value(true))
                .andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.summary.totalChanges").value(0))
                .andExpect(jsonPath("$.summary.highlights[0]").value("You're seeing your first market check for this watchlist."));

        assertThat(observationRepository.count()).isEqualTo(observations);
        assertThat(checkRepository.count()).isEqualTo(checks);
    }

    @Test
    void readingTheWatchlistDoesNotMoveTheCursor() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        addItem(ada, watchlistId, instrument("RELIANCE", "Reliance Industries").getId());

        MvcResult checked = mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/checks")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cursor").isNotEmpty())
                .andExpect(jsonPath("$.checkedAt").isNotEmpty())
                .andReturn();
        String cursor = JsonPath.read(checked.getResponse().getContentAsString(), "$.cursor");
        long observations = observationRepository.count();
        long checks = checkRepository.count();

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(cursor))
                .andExpect(jsonPath("$.firstCheck").value(false))
                .andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.summary.highlights[0]").value("No material changes since your last check."));

        assertThat(observationRepository.count()).isEqualTo(observations);
        assertThat(checkRepository.count()).isEqualTo(checks);
        assertThat(marketDataService.getQuote("RELIANCE").orElseThrow().synthetic()).isTrue();
        assertThat(marketDataService.getQuote("RELIANCE").orElseThrow().price())
                .isEqualByComparingTo(marketDataService.getQuote("RELIANCE").orElseThrow().price());
    }

    @Test
    void reportsPriceVolumeAndANewHighFromTheStoredReference() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        Instrument reliance = instrument("RELIANCE", "Reliance Industries");
        addItem(ada, watchlistId, reliance.getId());
        seedCheck(watchlistId, reliance, reference(reliance, "2000.00", "2400.00", "1900.00", 1_000_000L));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.priceMoves").value(1))
                .andExpect(jsonPath("$.summary.volumeSpikes").value(1))
                .andExpect(jsonPath("$.summary.newDayHighs").value(1))
                .andExpect(jsonPath("$.summary.newDayLows").value(0))
                .andExpect(jsonPath("$.summary.totalChanges").value(3))
                .andExpect(jsonPath("$.summary.instrumentsWithChanges").value(1))
                .andExpect(jsonPath("$.changes[?(@.type == 'PRICE_MOVE')].message",
                        hasItem("RELIANCE rose 25% since the previous observation.")))
                .andExpect(jsonPath("$.changes[?(@.type == 'PRICE_MOVE')].severity", hasItem("HIGH")))
                .andExpect(jsonPath("$.changes[?(@.type == 'VOLUME_SPIKE')].message",
                        hasItem("RELIANCE volume is 3.2x its previous reference volume.")))
                .andExpect(jsonPath("$.changes[?(@.type == 'NEW_DAY_HIGH')].message",
                        hasItem("RELIANCE is trading at a new observed day high.")))
                .andExpect(jsonPath("$.summary.highlights[0]").value("1 stock changed since the last check."));
    }

    @Test
    void reportsAPriceDecreaseAndANewLow() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        Instrument reliance = instrument("RELIANCE", "Reliance Industries");
        addItem(ada, watchlistId, reliance.getId());
        Quote live = marketDataService.getQuote("RELIANCE").orElseThrow();
        seedCheck(watchlistId, reliance, reference(reliance, "3000.00", "3100.00", "2600.00", live.volume()));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.priceMoves").value(1))
                .andExpect(jsonPath("$.summary.volumeSpikes").value(0))
                .andExpect(jsonPath("$.summary.newDayLows").value(1))
                .andExpect(jsonPath("$.summary.newDayHighs").value(0))
                .andExpect(jsonPath("$.changes[?(@.type == 'PRICE_MOVE')].message",
                        hasItem("RELIANCE fell 16.67% since the previous observation.")))
                .andExpect(jsonPath("$.changes[?(@.type == 'NEW_DAY_LOW')].message",
                        hasItem("RELIANCE is trading at a new observed day low.")));
    }

    @Test
    void reportsOnlyTheInstrumentThatMoved() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        Instrument reliance = instrument("RELIANCE", "Reliance Industries");
        Instrument tcs = instrument("TCS", "Tata Consultancy Services");
        addItem(ada, watchlistId, reliance.getId());
        addItem(ada, watchlistId, tcs.getId());
        Quote liveTcs = marketDataService.getQuote("TCS").orElseThrow();
        Watchlist watchlist = watchlistRepository.findById(UUID.fromString(watchlistId)).orElseThrow();
        WatchlistCheck check = checkRepository.saveAndFlush(new WatchlistCheck(
                watchlist, watchlist.getUser(), Instant.parse("2026-01-01T00:00:00Z")));
        checkItemRepository.saveAndFlush(new WatchlistCheckItem(
                check, reliance, reference(reliance, "2000.00", "2400.00", "1900.00", 1_000_000L)));
        checkItemRepository.saveAndFlush(new WatchlistCheckItem(check, tcs, observationFromQuote(tcs, liveTcs)));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.instrumentsWithChanges").value(1))
                .andExpect(jsonPath("$.changes[?(@.symbol == 'TCS')]").isEmpty())
                .andExpect(jsonPath("$.changes[?(@.symbol == 'RELIANCE')].type").isNotEmpty());
    }

    @Test
    void reportsMembershipChangesUntilTheNextCheck() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        Instrument reliance = instrument("RELIANCE", "Reliance Industries");
        Instrument tcs = instrument("TCS", "Tata Consultancy Services");
        addItem(ada, watchlistId, reliance.getId());

        MvcResult first = mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/checks")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isCreated())
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.cursor");
        String itemId = JsonPath.read(mockMvc.perform(get("/api/v1/watchlists/" + watchlistId)
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.items[0].id");

        addItem(ada, watchlistId, tcs.getId());
        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId + "/items/" + itemId)
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(cursor))
                .andExpect(jsonPath("$.summary.watchlistAdded").value(1))
                .andExpect(jsonPath("$.summary.watchlistRemoved").value(1))
                .andExpect(jsonPath("$.changes[?(@.type == 'WATCHLIST_ADDED')].message",
                        hasItem("TCS was added to this watchlist.")))
                .andExpect(jsonPath("$.changes[?(@.type == 'WATCHLIST_REMOVED')].message",
                        hasItem("RELIANCE was removed from this watchlist.")));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes")
                        .header("Authorization", bearer(ada))
                        .param("since", "2020-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(nullValue()))
                .andExpect(jsonPath("$.summary.watchlistAdded").value(1))
                .andExpect(jsonPath("$.summary.watchlistRemoved").value(0));

        MvcResult second = mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/checks")
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isCreated())
                .andReturn();
        String nextCursor = JsonPath.read(second.getResponse().getContentAsString(), "$.cursor");
        assertThat(nextCursor).isNotEqualTo(cursor);

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(nextCursor))
                .andExpect(jsonPath("$.changes").isEmpty());

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes")
                        .header("Authorization", bearer(ada))
                        .param("since", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(cursor))
                .andExpect(jsonPath("$.summary.watchlistAdded").value(1))
                .andExpect(jsonPath("$.summary.watchlistRemoved").value(1));
    }

    @Test
    void hidesAnotherUsersWatchlist() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");
        String watchlistId = createWatchlist(ada, "Long Term");

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes").header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Watchlist not found"));
    }

    @Test
    void rejectsAnotherUsersAcknowledgement() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");
        String watchlistId = createWatchlist(ada, "Long Term");

        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/checks").header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Watchlist not found"));
    }

    @Test
    void rejectsAMalformedCursor() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId + "/changes")
                        .header("Authorization", bearer(ada))
                        .param("since", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("since must be a check cursor or an ISO-8601 timestamp"));
    }

    @Test
    void deletesChecksWithTheWatchlist() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/checks").header("Authorization", bearer(ada)))
                .andExpect(status().isCreated());
        assertThat(checkRepository.countByWatchlist_Id(UUID.fromString(watchlistId))).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());

        assertThat(checkRepository.countByWatchlist_Id(UUID.fromString(watchlistId))).isZero();
    }

    private MarketObservation reference(
            Instrument instrument,
            String price,
            String dayHigh,
            String dayLow,
            long volume) {
        return observationRepository.saveAndFlush(new MarketObservation(
                instrument,
                MockMarketDataProvider.SOURCE,
                MockMarketDataProvider.SAMPLE_TIMESTAMP,
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(dayHigh),
                new BigDecimal(dayLow),
                volume,
                MockMarketDataProvider.CURRENCY,
                MockMarketDataProvider.SAMPLE_SESSION_DATE));
    }

    private MarketObservation observationFromQuote(Instrument instrument, Quote quote) {
        return observationRepository.saveAndFlush(new MarketObservation(
                instrument,
                MockMarketDataProvider.SOURCE,
                quote.timestamp(),
                quote.price(),
                quote.previousClose(),
                quote.open(),
                quote.high(),
                quote.low(),
                quote.volume(),
                quote.currency(),
                quote.sessionDate()));
    }

    private void seedCheck(String watchlistId, Instrument instrument, MarketObservation observation) {
        Watchlist watchlist = watchlistRepository.findById(UUID.fromString(watchlistId)).orElseThrow();
        WatchlistCheck check = checkRepository.saveAndFlush(new WatchlistCheck(
                watchlist, watchlist.getUser(), Instant.parse("2026-01-01T00:00:00Z")));
        checkItemRepository.saveAndFlush(new WatchlistCheckItem(check, instrument, observation));
    }

    private Instrument instrument(String symbol, String companyName) {
        return instrumentRepository.saveAndFlush(
                new Instrument(symbol, companyName, "NSE", "Test", InstrumentType.EQUITY));
    }

    private String token(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createWatchlist(String accessToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void addItem(String accessToken, String watchlistId, UUID instrumentId) throws Exception {
        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + instrumentId + "\"}"))
                .andExpect(status().isCreated());
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
