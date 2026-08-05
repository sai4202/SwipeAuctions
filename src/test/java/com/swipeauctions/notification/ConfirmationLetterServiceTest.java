package com.swipeauctions.notification;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationLetterServiceTest {

    @Test
    void generate_producesAPdf() {
        User winner = User.builder().email("winner@example.com").build();
        winner.setId(UUID.randomUUID());
        Category category = Category.builder().name("Bank Vehicles").build();
        Listing listing = Listing.builder().title("1965 Ford Mustang").category(category).build();
        Auction a = Auction.builder()
                .listing(listing).currentWinner(winner)
                .currentHighestBid(new BigDecimal("15000"))
                .currentEndTime(LocalDateTime.now())
                .build();
        a.setId(UUID.randomUUID());

        byte[] pdf = new ConfirmationLetterService().generate(a);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
