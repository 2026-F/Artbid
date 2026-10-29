package com.artbid.notification.controller;

import com.artbid.notification.domain.NotificationType;
import com.artbid.notification.dto.NotificationPageResponse;
import com.artbid.notification.dto.NotificationResponse;
import com.artbid.notification.exception.NotificationException;
import com.artbid.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

	@Mock
	NotificationService notificationService;

	MockMvc mockMvc;
	final Principal member = () -> "1";

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
				.setControllerAdvice(new NotificationExceptionHandler())
				.setMessageConverters(
						new StringHttpMessageConverter(StandardCharsets.UTF_8),
						new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	@Test
	void 내_알림_목록을_조회한다() throws Exception {
		NotificationResponse item = new NotificationResponse(5L, NotificationType.PAYMENT_COMPLETED, "결제 완료", "배송지를 입력해 주세요.",
				"/mypage", false, LocalDateTime.of(2026, 10, 29, 12, 0), null);
		when(notificationService.getMyNotifications(1L, 0, 20))
				.thenReturn(new NotificationPageResponse(List.of(item), 0, 20, 1, 1, 1));

		mockMvc.perform(get("/api/notifications").principal(member))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(5))
				.andExpect(jsonPath("$.content[0].type").value("PAYMENT_COMPLETED"))
				.andExpect(jsonPath("$.content[0].read").value(false))
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void 안_읽은_개수를_조회한다() throws Exception {
		when(notificationService.countUnread(1L)).thenReturn(3L);

		mockMvc.perform(get("/api/notifications/unread-count").principal(member))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(3));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		when(notificationService.getMyNotifications(null, 0, 20))
				.thenThrow(new NotificationException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다."));

		mockMvc.perform(get("/api/notifications"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void page가_숫자가_아니면_400() throws Exception {
		mockMvc.perform(get("/api/notifications").param("page", "abc").principal(member))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}
}
