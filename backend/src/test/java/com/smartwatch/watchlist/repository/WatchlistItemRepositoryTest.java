package com.smartwatch.watchlist.repository;

import com.smartwatch.marketdata.entity.Instrument;
import com.smartwatch.marketdata.entity.InstrumentType;
import com.smartwatch.marketdata.repository.InstrumentRepository;
import com.smartwatch.support.PostgresIntegrationTest;
import com.smartwatch.user.entity.User;
import com.smartwatch.user.repository.UserRepository;
import com.smartwatch.watchlist.entity.Watchlist;
import com.smartwatch.watchlist.entity.WatchlistItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WatchlistItemRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistItemRepository watchlistItemRepository;

    @Test
    void rejectsTheSameInstrumentTwiceInOneWatchlist() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        Instrument reliance = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist(user, "Long Term"));

        watchlistItemRepository.saveAndFlush(new WatchlistItem(watchlist, reliance));

        assertThat(watchlistItemRepository.findByWatchlistId(watchlist.getId())).hasSize(1);
        assertThatThrownBy(() -> watchlistItemRepository.saveAndFlush(new WatchlistItem(watchlist, reliance)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTheSameInstrumentOnDifferentWatchlists() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        Instrument reliance = instrumentRepository.saveAndFlush(equity("NSE", "RELIANCE"));
        Watchlist longTerm = watchlistRepository.saveAndFlush(new Watchlist(user, "Long Term"));
        Watchlist ideas = watchlistRepository.saveAndFlush(new Watchlist(user, "Trading Ideas"));

        watchlistItemRepository.saveAndFlush(new WatchlistItem(longTerm, reliance));
        watchlistItemRepository.saveAndFlush(new WatchlistItem(ideas, reliance));

        assertThat(watchlistRepository.findByUserId(user.getId())).hasSize(2);
    }

    @Test
    void rejectsDuplicateWatchlistNamesForOneUser() {
        User user = userRepository.saveAndFlush(new User("ada@example.com", "Ada"));
        User other = userRepository.saveAndFlush(new User("grace@example.com", "Grace"));
        watchlistRepository.saveAndFlush(new Watchlist(user, "Long Term"));
        watchlistRepository.saveAndFlush(new Watchlist(other, "Long Term"));

        assertThatThrownBy(() -> watchlistRepository.saveAndFlush(new Watchlist(user, "Long Term")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Instrument equity(String exchange, String symbol) {
        return new Instrument(symbol, symbol + " Co", exchange, "Test", InstrumentType.EQUITY);
    }
}
