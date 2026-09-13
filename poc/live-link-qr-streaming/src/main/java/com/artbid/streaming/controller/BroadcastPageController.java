package com.artbid.streaming.controller;

import com.artbid.auction.domain.BidItem;
import com.artbid.auction.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 방송자(노트북)가 여는 화면. 여기서 카메라를 켜서 시청자들에게 WebRTC로 직접 송출한다.
 * 시청자용 화면(/bid/{itemId})과는 별도 URL이며, QR/링크는 시청자에게만 공유한다.
 *
 * 카메라 접근(getUserMedia)은 브라우저 보안 정책상 HTTPS 또는 localhost에서만 허용되므로,
 * 이 화면은 반드시 방송 서버가 실행 중인 노트북에서 http://localhost:8080/broadcast/{itemId} 로 열어야 한다.
 */
@Controller
@RequiredArgsConstructor
public class BroadcastPageController {

    private final BidService bidService;

    @GetMapping("/broadcast/{itemId}")
    public String broadcastPage(@PathVariable String itemId, Model model) {
        BidItem item = bidService.findItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 itemId: " + itemId));
        model.addAttribute("itemId", item.getItemId());
        model.addAttribute("title", item.getTitle());
        return "broadcast";
    }
}
