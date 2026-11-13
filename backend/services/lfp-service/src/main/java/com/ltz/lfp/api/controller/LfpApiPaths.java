package com.ltz.lfp.api.controller;

/** Gateway path standardı: {@code /api/v1/lfp/**} (StripPrefix yoktur; controller tam path'i karşılar). */
public final class LfpApiPaths {

    public static final String BASE = "/api/v1/lfp";
    public static final String LISTINGS = BASE + "/listings";
    public static final String MY_LISTINGS = LISTINGS + "/mine";
    public static final String LISTING = LISTINGS + "/{listingId}";
    public static final String LISTING_CLOSE = LISTING + "/close";

    private LfpApiPaths() {
    }
}
