package com.vladislav.tgclone.bridge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.vladislav.tgclone.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/bindings")
public class TransportBindingController {

    private final TransportBindingService transportBindingService;

    public TransportBindingController(
        TransportBindingService transportBindingService
    ) {
        this.transportBindingService = transportBindingService;
    }

    @PostMapping
    public ResponseEntity<TransportBindingResponse> createBinding(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody CreateTransportBindingRequest request
    ) {
        TransportBinding saved = transportBindingService.createBinding(
            authenticatedUser,
            request.conversationId(),
            request.transport(),
            request.externalChatId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportBindingResponse.from(saved));
    }

    @GetMapping
    public List<TransportBindingResponse> listBindings(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @RequestParam Long conversationId
    ) {
        return transportBindingService.listBindings(authenticatedUser, conversationId).stream()
            .map(TransportBindingResponse::from)
            .toList();
    }

    @PatchMapping("/{bindingId}")
    public TransportBindingResponse updateBinding(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @PathVariable Long bindingId,
        @Valid @RequestBody UpdateTransportBindingRequest request
    ) {
        return TransportBindingResponse.from(
            transportBindingService.updateBinding(authenticatedUser, bindingId, request.active())
        );
    }
}

record CreateTransportBindingRequest(
    @NotNull(message = "conversationId is required")
    Long conversationId,
    @NotNull(message = "transport is required")
    BridgeTransport transport,
    @NotBlank(message = "externalChatId is required")
    String externalChatId
) {
}

record UpdateTransportBindingRequest(
    boolean active
) {
}

record TransportBindingResponse(
    Long id,
    Long conversationId,
    String transport,
    String externalChatId,
    boolean active,
    Instant createdAt
) {

    static TransportBindingResponse from(TransportBinding binding) {
        return new TransportBindingResponse(
            binding.getId(),
            binding.getConversation().getId(),
            binding.getTransport().name(),
            binding.getExternalChatId(),
            binding.isActive(),
            binding.getCreatedAt()
        );
    }
}
