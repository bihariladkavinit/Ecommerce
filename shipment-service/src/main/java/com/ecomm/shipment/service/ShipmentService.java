package com.ecomm.shipment.service;

import com.ecomm.shipment.dto.request.ShipmentStatusUpdateRequest;
import com.ecomm.shipment.dto.response.ShipmentResponse;
import com.ecomm.shipment.entity.Shipment;
import com.ecomm.shipment.entity.ShipmentEvent;
import com.ecomm.shipment.entity.ShipmentStatus;
import com.ecomm.shipment.exception.ConflictException;
import com.ecomm.shipment.exception.ResourceNotFoundException;
import com.ecomm.shipment.mapper.ShipmentMapper;
import com.ecomm.shipment.repository.ShipmentEventRepository;
import com.ecomm.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core shipment service — handles the three REST-facing operations:
 * <ol>
 *   <li>Look up a shipment by {@code orderId}.</li>
 *   <li>Look up a shipment by {@code trackingNumber} (public tracking).</li>
 *   <li>Advance shipment status (admin operation) with transition validation
 *       and {@link ShipmentEvent} history recording.</li>
 * </ol>
 *
 * <p>Shipment creation itself is triggered via Kafka (see
 * {@link com.ecomm.shipment.kafka.ShipmentCommandConsumer}) — that path owns
 * the initial CREATED status and outbox write. This service handles
 * post-creation lifecycle only.
 *
 * <h3>Status transition rules</h3>
 * Encoded in {@link ShipmentStatus#canTransitionTo(ShipmentStatus)}.
 * Valid progressions: {@code CREATED → DISPATCHED → DELIVERED}.
 * Both CREATED and DISPATCHED can also move to {@code CANCELLED}.
 * {@code DELIVERED} and {@code CANCELLED} are terminal — no further transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {

    private final ShipmentRepository      shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final ShipmentMapper          shipmentMapper;

    // ── Read ──────────────────────────────────────────────────────────

    /**
     * Returns the shipment associated with an order.
     *
     * @throws ResourceNotFoundException if no shipment exists for the order
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getByOrderId(UUID orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for orderId: " + orderId));
        return shipmentMapper.toResponse(shipment);
    }

    /**
     * Returns the shipment for a given tracking number (public endpoint).
     *
     * @throws ResourceNotFoundException if no shipment has that tracking number
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getByTrackingNumber(String trackingNumber) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for trackingNumber: " + trackingNumber));
        return shipmentMapper.toResponse(shipment);
    }

    // ── Write ─────────────────────────────────────────────────────────

    /**
     * Advances the shipment status to {@code request.status}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load shipment by {@code orderId} — 404 if absent.</li>
     *   <li>Validate the transition via {@link ShipmentStatus#canTransitionTo} —
     *       409 if invalid (e.g. DELIVERED → DISPATCHED).</li>
     *   <li>Update the shipment status and save.</li>
     *   <li>Append a {@link ShipmentEvent} row recording the transition — both
     *       in the same transaction so history is always consistent with current state.</li>
     * </ol>
     *
     * @param orderId the order whose shipment to update
     * @param request contains the desired target status
     * @return updated shipment
     * @throws ResourceNotFoundException if no shipment exists for the order
     * @throws ConflictException         if the transition is not allowed
     */
    @Transactional
    public ShipmentResponse updateStatus(UUID orderId, ShipmentStatusUpdateRequest request) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for orderId: " + orderId));

        ShipmentStatus current = shipment.getStatus();
        ShipmentStatus next    = request.getStatus();

        if (!current.canTransitionTo(next)) {
            throw new ConflictException(
                    "Invalid status transition: " + current + " → " + next);
        }

        shipment.setStatus(next);
        shipmentRepository.save(shipment);

        // Append immutable audit record — same transaction as the status update
        shipmentEventRepository.save(ShipmentEvent.builder()
                .shipmentId(shipment.getId())
                .status(next)
                .build());

        log.info("Shipment status updated orderId={} {} → {}", orderId, current, next);
        return shipmentMapper.toResponse(shipment);
    }
}
