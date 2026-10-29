-- 부하테스트용 ONGOING 경매 1건 생성.
-- 현재 코드에는 PREVIEW -> ONGOING 전환 로직이 없어서 API로는 입찰 가능한 경매를 만들 수 없다.
-- 그래서 DB에 직접 넣는다. 테이블은 bootRun을 한 번 실행해야 생긴다(ddl-auto: update).
--
-- 시각은 DB(UTC)와 JVM(KST) 시간대가 달라도 문제없도록 하루 단위로 넉넉하게 잡는다.
-- 입찰 API는 artwork를 조회하지 않으므로 artwork_id는 아무 값이나 넣어도 된다.
INSERT INTO auction (artwork_id, start_price, current_price, min_bid_unit,
                     preview_start, preview_end, auction_end_at, status)
VALUES (0, 100000, 100000, 1000,
        now() - interval '2 days', now() - interval '1 day', now() + interval '1 day',
        'ONGOING')
RETURNING id;
