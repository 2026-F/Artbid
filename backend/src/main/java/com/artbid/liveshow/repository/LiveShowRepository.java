package com.artbid.liveshow.repository;

import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.domain.LiveShowStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiveShowRepository extends JpaRepository<LiveShow, Long> {

	/** 시작, 종료, 다음 작품 열기를 동시에 눌러도 한 번만 반영되게 라이브 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
	@Query("select s from LiveShow s where s.id = :id")
	Optional<LiveShow> findByIdForUpdate(@Param("id") Long id);

	List<LiveShow> findByStatusOrderByIdDesc(LiveShowStatus status);
}
