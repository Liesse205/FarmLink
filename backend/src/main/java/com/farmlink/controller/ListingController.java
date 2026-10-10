package com.farmlink.controller;

import com.farmlink.dto.ListingCreateRequest;
import com.farmlink.dto.ListingFacetsResponse;
import com.farmlink.dto.ListingResponse;
import com.farmlink.dto.ListingUpdateRequest;
import com.farmlink.service.ListingService;
import com.farmlink.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final UserService userService;

    @GetMapping
    public List<ListingResponse> browse(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String location) {
        return listingService.search(search, category, location);
    }

    @GetMapping("/facets")
    public ListingFacetsResponse facets() {
        return listingService.facets();
    }

    @GetMapping("/mine")
    public List<ListingResponse> mine(Principal principal) {
        return listingService.mine(userService.getCurrentUser(principal));
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id) {
        return listingService.get(id);
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@Valid @RequestBody ListingCreateRequest request,
                                                  Principal principal) {
        ListingResponse listing = listingService.create(request, userService.getCurrentUser(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(listing);
    }

    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ListingUpdateRequest request,
                                  Principal principal) {
        return listingService.update(id, request, userService.getCurrentUser(principal));
    }
}
