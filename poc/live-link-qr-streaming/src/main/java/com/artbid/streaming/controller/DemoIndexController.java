package com.artbid.streaming.controller;

import com.artbid.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 데모 아이템 목록 + 각 아이템의 고정 링크/QR 미리보기를 보여주는 확인용 페이지.
 * 실 서비스의 "위탁자용 관리 화면"에 해당하는 자리이지만, 이 PoC에서는
 * 검증 대상이 아니므로 최소한의 확인용으로만 둔다. (목록 자체는 /demo 로 이동)
 *
 * 루트("/")는 실제 사용 흐름(주소로 들어가면 바로 영상+입찰 화면)을 그대로
 * 재현하기 위해 기본 데모 아이템(item1)의 입찰 페이지로 바로 이동시킨다.
 */
@Controller
@RequiredArgsConstructor
public class DemoIndexController {

    private final AppProperties appProperties;

    @GetMapping("/")
    public String root() {
        return "redirect:/bid/item1";
    }

    @GetMapping("/demo")
    public String demoList(Model model) {
        model.addAttribute("itemIds", List.copyOf(appProperties.getDemoItems().keySet()));
        return "index";
    }
}
