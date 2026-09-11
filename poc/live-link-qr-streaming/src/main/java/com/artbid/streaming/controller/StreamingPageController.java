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
 * 시청자가 고정 링크/QR로 진입하는 입찰(시청) 페이지.
 * 이 URL(/bid/{itemId}) 자체가 "고정 링크"이며 QrCodeController가 인코딩하는 대상이다.
 *
 * 실 서비스에서는 여기서 보여줄 영상이 AWS IVS의 HLS 재생 URL이 되지만,
 * 이 PoC에서는 유튜브 iframe 임베드로 대체해 "링크/QR 진입 + 실시간 호가 반영" 흐름만 검증한다.
 */
@Controller
@RequiredArgsConstructor
public class StreamingPageController {

    private final BidService bidService;

    @GetMapping("/bid/{itemId}")
    public String bidPage(@PathVariable String itemId, Model model) {
        BidItem item = bidService.findItem(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 itemId: " + itemId));

        // 페이지에 진입한 시점에 "다른 시청자들의 입찰"을 흉내 내는 자동 시뮬레이션을 시작한다.
        // -> 시청자는 아무것도 안 해도 실시간으로 호가가 바뀌는 것을 바로 확인할 수 있다.
        bidService.startAutoSimulation(itemId);

        model.addAttribute("itemId", item.getItemId());
        model.addAttribute("title", item.getTitle());
        model.addAttribute("youtubeVideoId", item.getYoutubeVideoId());
        model.addAttribute("initialPrice", item.getCurrentPrice());
        model.addAttribute("initialBidCount", item.getBidCount());
        return "bid";
    }
}
