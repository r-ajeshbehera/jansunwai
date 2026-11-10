package com.jansunwai.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

final class Pages {
    static final int MAX_SIZE = 100;

    private Pages() { }

    /** Clients cannot request huge pages (?size=1000000 would load whole tables). */
    static Pageable capped(Pageable p) {
        return p.getPageSize() <= MAX_SIZE ? p : PageRequest.of(p.getPageNumber(), MAX_SIZE, p.getSort());
    }
}
