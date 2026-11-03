// ramp2.js
// 목적: 100명으로는 전혀 안 터졌으니, 훨씬 높은 동시접속까지 밀어붙여서
//       진짜 한계점(응답시간 튀는 지점, 실패율 올라가는 지점)을 찾는다
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    ramp_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 100 },  // 1단계: 이전에 확인한 수준
        { duration: '30s', target: 100 },
        { duration: '20s', target: 300 },  // 2단계: 3배
        { duration: '30s', target: 300 },
        { duration: '20s', target: 600 },  // 3단계: 6배
        { duration: '30s', target: 600 },
        { duration: '20s', target: 1000 }, // 4단계: 10배 — 여기서 뭔가 보일 가능성 큼
        { duration: '40s', target: 1000 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 이번엔 기준을 깐깐하게 잡아서, 넘는 순간부터가 "한계점"이라고 보면 됨
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  const res = http.get('http://52.79.225.228:8080/api/artworks');
  check(res, { '상태코드 200': (r) => r.status === 200 });
  sleep(0.3); // 쉬는 시간을 줄여서 VU 1명당 더 자주 요청하게 함 (더 강한 부하)
}
