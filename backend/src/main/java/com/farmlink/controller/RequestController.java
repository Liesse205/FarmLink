package com.farmlink.controller;

import com.farmlink.dto.PurchaseRequestCreateRequest;
import com.farmlink.dto.PurchaseRequestResponse;
import com.farmlink.service.PurchaseRequestService;
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
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final PurchaseRequestService requestService;
    private final UserService userService;

    @GetMapping
    public List<PurchaseRequestResponse> list(Principal principal) {
        return requestService.list(userService.getCurrentUser(principal));
    }

    @PostMapping
    public ResponseEntity<PurchaseRequestResponse> create(@Valid @RequestBody PurchaseRequestCreateRequest request,
                                                          Principal principal) {
        PurchaseRequestResponse created =
                requestService.create(request, userService.getCurrentUser(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/accept")
    public PurchaseRequestResponse accept(@PathVariable Long id, Principal principal) {
        return requestService.accept(id, userService.getCurrentUser(principal));
    }

    @PutMapping("/{id}/reject")
    public PurchaseRequestResponse reject(@PathVariable Long id, Principal principal) {
        return requestService.reject(id, userService.getCurrentUser(principal));
    }
}
