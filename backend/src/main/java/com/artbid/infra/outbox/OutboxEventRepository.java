package com.artbid.infra.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * - 나중에 경매 서비스를 여러 인스턴스로 띄우면, 여러 인스턴스가 동시에 폴링할 수 있음.
     * - 잠금 없으면 같은 이벤트를 두 인스턴스가 동시에 집어가서 중복 발행될 수 있음.
     * - SKIP LOCKED를 쓰면, 다른 인스턴스가 이미 잠근 행은 그냥 건너뛰고 다음 행을 가져감
     *   (기다리지 않아서 빠르고, 중복도 안 생김).
     */

    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);
}