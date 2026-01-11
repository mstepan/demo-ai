import http from 'k6/http';
import { check } from 'k6';

export const options = {
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<5000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7171';

export default function () {
  const url = `${BASE_URL}/ask/stream`;
  const payload = JSON.stringify({ question: 'Say hello in a few short chunks.' });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/x-ndjson',
    },
    timeout: '180s',
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'content-type is ndjson': (r) => {
      const ct = (r.headers['Content-Type'] || '').toLowerCase();
      return ct.includes('application/x-ndjson') || ct.includes('application/ndjson');
    },
    'has at least one line': (r) => (r.body || '').split('\n').filter(Boolean).length > 0,
    'lines parse to strings and combined text non-empty': (r) => {
      try {
        const lines = (r.body || '').split('\n').filter(Boolean);
        if (lines.length === 0) return false;
        const chunks = lines.map((line) => JSON.parse(line));
        if (!chunks.every((c) => typeof c === 'string')) return false;
        const joined = chunks.join('').trim();
        return joined.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
}
