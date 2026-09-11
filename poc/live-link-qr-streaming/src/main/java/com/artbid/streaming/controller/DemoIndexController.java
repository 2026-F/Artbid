package com.artbid.streaming.controller;

import com.artbid.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 데모 아이템 목록 + 각 아이템의 고정 링크/QR 미리보기를 보여주는 진입 페이지.
 * 실 서비스의 "위탁자용 관리 화면"에 해당하는 자리이지만, 이 PoC에서는
 * 검증 대상이 아니므로 최소한의 확인용으로만 둔다.
 */
@Controller
@RequiredArgsConstructor
public class DemoIndexController {

    private final AppProperties appProperties;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("itemIds", List.copyOf(appProperties.getDemoItems().keySet()));
        return "index";
    }
}
