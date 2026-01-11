import http from 'k6/http';
import { check } from 'k6';

export const options = {
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<15000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7171';

export default function () {
  const url = `${BASE_URL}/ask`;
  const payload = JSON.stringify({ question: 'Say hello in one short sentence.' });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    timeout: '120s',
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'content-type is json': (r) => (r.headers['Content-Type'] || '').toLowerCase().includes('application/json'),
    'has non-empty answer field': (r) => {
      try {
        const data = JSON.parse(r.body);
        return typeof data.answer === 'string' && data.answer.trim().length > 0;
      } catch (e) {
        return false;
      }
    },
  });
}
