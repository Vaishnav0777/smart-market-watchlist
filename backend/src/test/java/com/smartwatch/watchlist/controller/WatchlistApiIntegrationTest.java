package com.smartwatch.watchlist.controller;

import com.jayway.jsonpath.JsonPath;
import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.user.repository.RefreshSessionRepository;
import com.smartwatch.user.repository.UserRepository;
import com.smartwatch.watchlist.entity.WatchlistItem;
import com.smartwatch.watchlist.repository.WatchlistItemRepository;
import com.smartwatch.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchlistApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "strong-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mockMvc.perform(post("/api/v1/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long Term\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/watchlists/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsAndRenamesOnlyTheOwnersList() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String grace = token("grace@example.com", "Grace");

        String adaList = createWatchlist(ada, "  Long Term  ");
        String graceList = createWatchlist(grace, "Ideas");

        String firstList = mockMvc.perform(get("/api/v1/watchlists").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(adaList))
                .andExpect(jsonPath("$[0].name").value("Long Term"))
                .andExpect(jsonPath("$[0].itemCount").value(0))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondList = mockMvc.perform(get("/api/v1/watchlists").header("Authorization", bearer(ada)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(secondList).isEqualTo(firstList);

        mockMvc.perform(get("/api/v1/watchlists/" + adaList).header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Long Term"))
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(patch("/api/v1/watchlists/" + adaList)
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Core Holdings\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Core Holdings"))
                .andExpect(jsonPath("$.itemCount").value(0));

        mockMvc.perform(get("/api/v1/watchlists/" + graceList).header("Authorization", bearer(grace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ideas"));

        mockMvc.perform(get("/api/v1/watchlists/" + adaList).header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Watchlist not found"));
    }

    @Test
    void hidesMissingAndForeignWatchlistsWithTheSameNotFoundResponse() throws Exception {
        String grace = token("grace@example.com", "Grace");
        mockMvc.perform(get("/api/v1/watchlists/" + UUID.randomUUID()).header("Authorization", bearer(grace)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Watchlist not found"));
    }

    @Test
    void addsInstrumentsAndKeepsQuotesOutOfTheWatchlistRow() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        UUID relianceId = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "Stored Reliance Name", "NSE", "Energy", InstrumentType.EQUITY)).getId();
        UUID otherListingId = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "BSE Reliance", "BSE", "Energy", InstrumentType.EQUITY)).getId();

        MvcResult added = mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + relianceId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.addedAt").isNotEmpty())
                .andExpect(jsonPath("$.instrument.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.instrument.displayName").value("Stored Reliance Name"))
                .andExpect(jsonPath("$.instrument.exchange").value("NSE"))
                .andExpect(jsonPath("$.quote").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String itemId = JsonPath.read(added.getResponse().getContentAsString(), "$.id");
        Instant updatedAt = itemRepository.findById(UUID.fromString(itemId)).orElseThrow().getUpdatedAt();

        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + otherListingId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].instrument.displayName").value("Stored Reliance Name"))
                .andExpect(jsonPath("$.items[0].quote.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.items[0].quote.exchange").value("NSE"))
                .andExpect(jsonPath("$.items[0].quote.price").value(2500.00))
                .andExpect(jsonPath("$.items[0].quote.synthetic").value(true))
                .andExpect(jsonPath("$.items[0].price").doesNotExist())
                .andExpect(jsonPath("$.items[1].instrument.exchange").value("BSE"))
                .andExpect(jsonPath("$.items[1].quote").value(nullValue()));

        WatchlistItem stored = itemRepository.findById(UUID.fromString(itemId)).orElseThrow();
        assertThat(stored.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(stored.getInstrument().getCompanyName()).isEqualTo("Stored Reliance Name");

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemCount").value(2));
    }

    @Test
    void rejectsAnUnknownInstrument() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");

        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Instrument not found"));
    }

    @Test
    void rejectsADuplicateInstrument() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        UUID relianceId = instrumentRepository.saveAndFlush(
                new Instrument("RELIANCE", "Reliance Industries", "NSE", "Energy", InstrumentType.EQUITY)).getId();
        addItem(ada, watchlistId, relianceId);

        mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + relianceId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This instrument is already on the watchlist"));
    }

    @Test
    void removesAnItemWithoutRemovingTheWatchlist() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        UUID instrumentId = instrumentRepository.saveAndFlush(
                new Instrument("TCS", "Tata Consultancy Services", "NSE", "Information Technology", InstrumentType.EQUITY))
                .getId();
        String itemId = addItem(ada, watchlistId, instrumentId);

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId + "/items/" + itemId)
                        .header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(itemRepository.findByWatchlistId(UUID.fromString(watchlistId))).isEmpty();
    }

    @Test
    void reordersItemsInOneStep() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        UUID first = instrumentRepository.saveAndFlush(equity("AAA")).getId();
        UUID second = instrumentRepository.saveAndFlush(equity("BBB")).getId();
        UUID third = instrumentRepository.saveAndFlush(equity("CCC")).getId();
        String firstItem = addItem(ada, watchlistId, first);
        String secondItem = addItem(ada, watchlistId, second);
        String thirdItem = addItem(ada, watchlistId, third);

        mockMvc.perform(put("/api/v1/watchlists/" + watchlistId + "/items/order")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemIds":["%s","%s","%s"]}
                                """.formatted(thirdItem, firstItem, secondItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(thirdItem))
                .andExpect(jsonPath("$.items[0].position").value(0))
                .andExpect(jsonPath("$.items[1].id").value(firstItem))
                .andExpect(jsonPath("$.items[1].position").value(1))
                .andExpect(jsonPath("$.items[2].id").value(secondItem))
                .andExpect(jsonPath("$.items[2].position").value(2))
                .andExpect(jsonPath("$.items[0].quote").value(nullValue()));

        List<WatchlistItem> stored = itemRepository.findDetailedByWatchlistId(UUID.fromString(watchlistId));
        assertThat(stored).extracting(item -> item.getId().toString())
                .containsExactly(thirdItem, firstItem, secondItem);
        assertThat(stored).extracting(WatchlistItem::getSortOrder).containsExactly(0, 1, 2);
    }

    @Test
    void deletesTheWatchlistAndItsItems() throws Exception {
        String ada = token("ada@example.com", "Ada");
        String watchlistId = createWatchlist(ada, "Long Term");
        UUID instrumentId = instrumentRepository.saveAndFlush(equity("INFY")).getId();
        addItem(ada, watchlistId, instrumentId);

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                .andExpect(status().isNoContent());

        assertThat(watchlistRepository.findById(UUID.fromString(watchlistId))).isEmpty();
        assertThat(itemRepository.findByWatchlistId(UUID.fromString(watchlistId))).isEmpty();
        assertThat(instrumentRepository.findById(instrumentId)).isPresent();
    }

    @Test
    void rejectsInvalidWatchlistInput() throws Exception {
        String ada = token("ada@example.com", "Ada");
        createWatchlist(ada, "Long Term");

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid"));

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", bearer(ada))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long Term\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A watchlist with this name already exists"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectedChangesDoNotAlterAnotherUsersWatchlistOrItemOrder() throws Exception {
        String adaEmail = "ada-" + UUID.randomUUID() + "@example.com";
        String graceEmail = "grace-" + UUID.randomUUID() + "@example.com";
        List<UUID> instrumentIds = new ArrayList<>();
        try {
            String ada = token(adaEmail, "Ada");
            String grace = token(graceEmail, "Grace");
            String watchlistId = createWatchlist(ada, "Long Term");
            UUID firstInstrument = instrumentRepository.saveAndFlush(equity("ZZ" + UUID.randomUUID().toString().substring(0, 8))).getId();
            UUID secondInstrument = instrumentRepository.saveAndFlush(equity("ZZ" + UUID.randomUUID().toString().substring(0, 8))).getId();
            instrumentIds.add(firstInstrument);
            instrumentIds.add(secondInstrument);
            String firstItem = addItem(ada, watchlistId, firstInstrument);
            String secondItem = addItem(ada, watchlistId, secondInstrument);
            String otherWatchlist = createWatchlist(ada, "Other");
            String foreignItem = addItem(ada, otherWatchlist, firstInstrument);

            mockMvc.perform(patch("/api/v1/watchlists/" + watchlistId)
                            .header("Authorization", bearer(grace))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Stolen\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Watchlist not found"));

            mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(grace)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                            .header("Authorization", bearer(grace))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instrumentId\":\"" + secondInstrument + "\"}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete("/api/v1/watchlists/" + watchlistId + "/items/" + firstItem)
                            .header("Authorization", bearer(grace)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/api/v1/watchlists/" + watchlistId + "/items/order")
                            .header("Authorization", bearer(grace))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"itemIds":["%s","%s"]}
                                    """.formatted(secondItem, firstItem)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/api/v1/watchlists/" + watchlistId + "/items/order")
                            .header("Authorization", bearer(ada))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"itemIds":["%s","%s"]}
                                    """.formatted(firstItem, firstItem)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Reorder must include each watchlist item exactly once"));

            mockMvc.perform(put("/api/v1/watchlists/" + watchlistId + "/items/order")
                            .header("Authorization", bearer(ada))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"itemIds":["%s","%s"]}
                                    """.formatted(foreignItem, secondItem)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Reorder must include each watchlist item exactly once"));

            mockMvc.perform(put("/api/v1/watchlists/" + watchlistId + "/items/order")
                            .header("Authorization", bearer(ada))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"itemIds\":[\"" + firstItem + "\"]}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/v1/watchlists/" + watchlistId).header("Authorization", bearer(ada)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Long Term"))
                    .andExpect(jsonPath("$.items[0].id").value(firstItem))
                    .andExpect(jsonPath("$.items[0].position").value(0))
                    .andExpect(jsonPath("$.items[1].id").value(secondItem))
                    .andExpect(jsonPath("$.items[1].position").value(1));
        } finally {
            cleanup(adaEmail, instrumentIds);
            cleanup(graceEmail, List.of());
        }
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

    private String addItem(String accessToken, String watchlistId, UUID instrumentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/watchlists/" + watchlistId + "/items")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentId\":\"" + instrumentId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void cleanup(String email, List<UUID> instrumentIds) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userRepository.findByEmail(email).ifPresent(user -> {
                UUID userId = user.getId();
                List<UUID> watchlistIds = watchlistRepository.findByUserId(userId).stream()
                        .map(watchlist -> watchlist.getId())
                        .toList();
                for (UUID watchlistId : watchlistIds) {
                    itemRepository.deleteByWatchlistId(watchlistId);
                    watchlistRepository.deleteById(watchlistId);
                }
                refreshSessionRepository.deleteAll(refreshSessionRepository.findByUserId(userId));
                userRepository.deleteById(userId);
            });
            if (!instrumentIds.isEmpty()) {
                instrumentRepository.deleteAllById(instrumentIds);
            }
        });
    }

    private static Instrument equity(String symbol) {
        return new Instrument(symbol, symbol + " Co", "NSE", "Test", InstrumentType.EQUITY);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
