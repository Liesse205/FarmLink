package com.farmlink.dto;

import com.farmlink.entity.PurchaseRequest;
import com.farmlink.entity.User;

/**
 * Contact information shared between the two participants of an <b>ACCEPTED</b>
 * purchase request: the buyer and the farmer who owns the listing. Never included
 * in any other response — it is produced only by
 * {@code GET /api/requests/{id}/contact} after the service layer has verified the
 * caller is one of the two participants and the request was accepted.
 *
 * @param viewerId   the authenticated user who is calling, so the frontend can show
 *                   the counterparty's details without guessing
 * @param farmer     the listing owner's contact details
 * @param buyer      the requesting buyer's contact details
 * @param instruction short guidance on what to do next
 */
public record RequestContactResponse(
        Long requestId,
        Long listingId,
        String listingName,
        Long viewerId,
        ContactDetails farmer,
        ContactDetails buyer,
        String instruction) {

    public record ContactDetails(Long userId, String name, String email, String phone) {

        static ContactDetails of(User user) {
            return new ContactDetails(user.getId(), user.getName(), user.getEmail(), user.getPhone());
        }
    }

    public static final String INSTRUCTION =
            "Your request was accepted. Use these contact details to coordinate collection or delivery.";

    public static RequestContactResponse from(PurchaseRequest request, Long viewerId) {
        return new RequestContactResponse(
                request.getId(),
                request.getListing().getId(),
                request.getListing().getName(),
                viewerId,
                ContactDetails.of(request.getListing().getFarmer()),
                ContactDetails.of(request.getBuyer()),
                INSTRUCTION);
    }
}
