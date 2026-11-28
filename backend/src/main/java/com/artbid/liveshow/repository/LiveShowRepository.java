package com.artbid.liveshow.repository;

import com.artbid.liveshow.domain.LiveShow;
import com.artbid.liveshow.domain.LiveShowStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LiveShowRepository extends JpaRepository<LiveShow, Long> {

	/** 시작, 종료, 다음 작품 열기를 동시에 눌러도 한 번만 반영되게 라이브 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
	@Query("select s from LiveShow s where s.id = :id")
	Optional<LiveShow> findByIdForUpdate(@Param("id") Long id);

	List<LiveShow> findByStatusOrderByIdDesc(LiveShowStatus status);

	/** 정리할 스테이지가 남은 라이브. 한 주기에 너무 많이 AWS를 부르지 않게 50개씩 본다. */
	List<LiveShow> findTop50ByStatusAndStageArnIsNotNullOrderByIdAsc(LiveShowStatus status);

	/** 스테이지를 지운 뒤 stageArn을 비운다. 그 사이 값이 바뀌었으면(다른 스테이지) 건드리지 않는다. */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("update LiveShow s set s.stageArn = null where s.id = :id and s.stageArn = :stageArn")
	int clearStageArn(@Param("id") Long id, @Param("stageArn") String stageArn);
}
