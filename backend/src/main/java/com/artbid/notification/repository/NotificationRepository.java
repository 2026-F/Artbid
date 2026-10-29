package com.artbid.notification.repository;

import com.artbid.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

	long countByMemberIdAndReadAtIsNull(Long memberId);

	// 모두 읽음: 한 번의 UPDATE로 처리하고 바뀐 건수를 돌려준다
	@Modifying(clearAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.memberId = :memberId and n.readAt is null")
	int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
