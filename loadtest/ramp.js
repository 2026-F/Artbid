// ramp.js
// 목적: 트래픽을 천천히 올려가면서(5 -> 20 -> 50 -> 100명)
//       그라파나에서 CPU/Heap/DB커넥션풀이 어디서부터 힘들어하는지 관찰하는 스크립트
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    ramp_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 },   // 5명까지 증가
        { duration: '40s', target: 5 },   // 5명 유지하면서 관찰
        { duration: '20s', target: 20 },  // 20명까지 증가
        { duration: '40s', target: 20 },  // 20명 유지
        { duration: '20s', target: 50 },  // 50명까지 증가
        { duration: '40s', target: 50 },  // 50명 유지
        { duration: '20s', target: 100 }, // 100명까지 증가 (여기서 뭔가 보일 가능성 큼)
        { duration: '40s', target: 100 }, // 100명 유지
        { duration: '20s', target: 0 },   // 서서히 종료
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],      // 실패율 5% 넘으면 FAIL 표시
    http_req_duration: ['p(95)<2000'],   // 95%가 2초 넘으면 경고
  },
};

export default function () {
  // 백엔드 메인 API 포트(8080)로 평상시 조회 트래픽을 흉내냄
  const res = http.get('http://52.79.225.228:8080/api/artworks');
  check(res, { '상태코드 200': (r) => r.status === 200 });
  sleep(1);
}
