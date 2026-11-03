// ramp3.js
// 목적: 100~1000 사이에서 정확히 몇 명부터 CPU가 포화되는지 핀포인트로 찾기
// 각 단계를 30초씩 충분히 유지해서, 그라파하프에서도 단계별 구간이 뚜렷하게 구분되게 함
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    ramp_traffic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '30s', target: 100 },  // 100명 구간
        { duration: '10s', target: 200 },
        { duration: '30s', target: 200 },  // 200명 구간
        { duration: '10s', target: 300 },
        { duration: '30s', target: 300 },  // 300명 구간
        { duration: '10s', target: 400 },
        { duration: '30s', target: 400 },  // 400명 구간
        { duration: '10s', target: 500 },
        { duration: '30s', target: 500 },  // 500명 구간
        { duration: '10s', target: 600 },
        { duration: '30s', target: 600 },  // 600명 구간
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.1'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const res = http.get('http://52.79.225.228:8080/api/artworks');
  check(res, { '상태코드 200': (r) => r.status === 200 });
  sleep(0.3);
}
