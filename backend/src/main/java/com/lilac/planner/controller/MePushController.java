package com.lilac.planner.controller;

import com.lilac.planner.dto.PushSubscriptionDto;
import com.lilac.planner.dto.PushSubscriptionRequest;
import com.lilac.planner.dto.TimezoneRequest;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.service.CurrentUserResolver;
import com.lilac.planner.service.PlannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/me")
public class MePushController {

    private final PlannerService planner;
    private final CurrentUserResolver resolver;

    public MePushController(PlannerService planner, CurrentUserResolver resolver) {
        this.planner = planner;
        this.resolver = resolver;
    }

    @PostMapping("/push-subscriptions")
    public PushSubscriptionDto register(@Valid @RequestBody PushSubscriptionRequest req,
                                         @AuthenticationPrincipal Jwt jwt) {
        var user = resolver.resolve(jwt);
        PushSubscription subscription = new PushSubscription(user.getId(), req.platform(), req.token());
        subscription.setP256dh(req.p256dh());
        subscription.setAuth(req.auth());
        return PushSubscriptionDto.from(planner.registerPushSubscription(subscription));
    }

    @GetMapping("/push-subscriptions")
    public List<PushSubscriptionDto> list(@AuthenticationPrincipal Jwt jwt) {
        var user = resolver.resolve(jwt);
        return planner.listPushSubscriptions(user.getId()).stream().map(PushSubscriptionDto::from).toList();
    }

    @DeleteMapping("/push-subscriptions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        var user = resolver.resolve(jwt);
        if (!planner.deletePushSubscription(user.getId(), id)) {
            throw new NoSuchElementException("No push subscription with id " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/timezone")
    public ResponseEntity<Void> updateTimezone(@Valid @RequestBody TimezoneRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        try {
            ZoneId.of(req.timezone());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timezone must be a valid IANA zone id");
        }
        var user = resolver.resolve(jwt);
        planner.updateUserTimezone(user.getId(), req.timezone());
        return ResponseEntity.noContent().build();
    }
}
