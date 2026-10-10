package com.farmlink.dto;

import java.util.List;

public record ListingFacetsResponse(
        List<String> categories,
        List<String> locations) {
}
