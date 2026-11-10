package com.jansunwai.service;

/** Published inside the submit transaction; the AI pipeline runs only AFTER COMMIT, on a background thread. */
public record ComplaintSubmittedEvent(Long complaintId) { }
