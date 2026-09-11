package com.artbid.auction.controller;

import com.artbid.auction.domain.BidItem;
import com.artbid.auction.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 수동 테스트/시뮬레이션 제어용 REST API.
 */
@RestController
@RequiredArgsConstructor
public class BidApiController {

    private final BidService bidService;

    /** "테스트용 입찰하기" 버튼에서 호출. 즉시 호가를 올리고 WebSocket으로 브로드캐스트한다. */
    @PostMapping("/api/bid/{itemId}/manual")
    public ResponseEntity<Map<String, Object>> manualBid(
            @PathVariable String itemId,
            @RequestParam(defaultValue = "5000") long increment) {
        BidItem item = bidService.placeManualBid(itemId, increment);
        return ResponseEntity.ok(Map.of(
                "itemId", item.getItemId(),
                "currentPrice", item.getCurrentPrice(),
                "bidCount", item.getBidCount()
        ));
    }

    @PostMapping("/api/bid/{itemId}/simulate/start")
    public ResponseEntity<Void> startSimulation(@PathVariable String itemId) {
        bidService.startAutoSimulation(itemId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/bid/{itemId}/simulate/stop")
    public ResponseEntity<Void> stopSimulation(@PathVariable String itemId) {
        bidService.stopAutoSimulation(itemId);
        return ResponseEntity.ok().build();
    }
}
