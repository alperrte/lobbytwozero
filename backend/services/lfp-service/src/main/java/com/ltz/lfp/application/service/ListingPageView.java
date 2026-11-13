package com.ltz.lfp.application.service;

import java.util.List;

/** Bir ilan sayfası; {@code nextCursor} son sayfada {@code null}. */
public record ListingPageView(List<ListingView> items, String nextCursor) {
}
