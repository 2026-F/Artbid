// 한 경매에 입찰이 몰리는 상황을 재현한다.
// 각 VU가 현재가를 조회하고 "현재가 + 최소 입찰 단위"로 입찰하므로,
// 동시에 같은 가격을 낸 요청 중 하나만 성공하고 나머지는 409로 거절된다.
//
// 응답 코드 의미
//   200 입찰 성공
//   409 가격 경합으로 거절 (정상 동작이므로 실패로 세지 않음)
//   503 행 락 대기 3초 초과 (BidTemporarilyUnavailableException) -> 핵심 관찰 대상
//   그 외 서버 오류
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const AUCTION_ID = __ENV.AUCTION_ID;
const PEAK_VUS = Number(__ENV.PEAK_VUS || 100);

if (!AUCTION_ID) {
  throw new Error('AUCTION_ID 환경변수가 필요하다. loadtest/seed.sql 실행 결과의 id를 넣는다.');
}

const bidAccepted = new Counter('bid_accepted');
const bidRejected = new Counter('bid_rejected');
const bidLockTimeout = new Rate('bid_lock_timeout');
const bidServerError = new Rate('bid_server_error');

// 409, 503은 의도된 응답이라 http_req_failed에서 제외하고 위 커스텀 지표로 따로 센다
http.setResponseCallback(http.expectedStatuses(200, 409, 503));

export const options = {
  scenarios: {
    bid_contention: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: Math.ceil(PEAK_VUS / 2) },
        { duration: '1m', target: PEAK_VUS },
        { duration: '1m', target: PEAK_VUS },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{name:bid}': ['p(95)<500'],
    bid_lock_timeout: ['rate<0.01'],
    bid_server_error: ['rate==0'],
  },
};

export default function () {
  const priceRes = http.get(`${BASE_URL}/api/auctions/${AUCTION_ID}/current-price`, {
    tags: { name: 'current-price' },
  });
  if (!check(priceRes, { '현재가 조회 성공': (r) => r.status === 200 })) {
    sleep(1);
    return;
  }

  const auction = priceRes.json();
  const price = auction.currentPrice + auction.minBidUnit;

  const res = http.post(
    `${BASE_URL}/api/auctions/${AUCTION_ID}/bids`,
    JSON.stringify({ bidderId: __VU, price }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'bid' } },
  );

  if (res.status === 200) bidAccepted.add(1);
  if (res.status === 409) bidRejected.add(1);
  bidLockTimeout.add(res.status === 503);
  bidServerError.add(res.status >= 500 && res.status !== 503);

  check(res, { '입찰 응답이 200/409/503 중 하나': (r) => [200, 409, 503].includes(r.status) });

  sleep(Math.random() * 0.5); // 사람이 연타하는 정도의 간격
}
