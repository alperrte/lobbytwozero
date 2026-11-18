package com.ltz.review.application.service;

import com.ltz.review.api.dto.response.PersonalRatingsResponse;
import com.ltz.review.infrastructure.persistence.repository.AccountWriteLock;
import com.ltz.review.infrastructure.persistence.repository.RatingRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalRatingsService {
    private final RatingRepository ratings;
    private final AccountWriteLock accounts;

    public PersonalRatingsService(RatingRepository ratings, AccountWriteLock accounts) {
        this.ratings = ratings;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, timeout = 4)
    public PersonalRatingsResponse get(UUID owner, String after, int size) {
        var cursor = after == null ? firstPage(owner) : PersonalRatingCursor.decode(after, owner);
        var rows = ratings.findPage(owner, cursor.after(), cursor.before(), size + 1);
        if (rows.size() <= size) return new PersonalRatingsResponse(rows, null, false);

        var items = List.copyOf(rows.subList(0, size));
        if (cursor.page() == PersonalRatingCursor.MAX_PAGE) return new PersonalRatingsResponse(items, null, true);
        var next = new PersonalRatingCursor(items.getLast().gameId(), cursor.before(), cursor.page() + 1);
        return new PersonalRatingsResponse(items, next.encode(owner), false);
    }

    private PersonalRatingCursor firstPage(UUID owner) {
        accounts.acquireShared(owner);
        return new PersonalRatingCursor(null, ratings.readTime(), 1);
    }
}
