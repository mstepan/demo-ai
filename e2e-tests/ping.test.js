import http from 'k6/http';
import {check} from 'k6';

export const options = {
    thresholds: {
        http_req_failed: ['rate==0'],
        http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:7171';

export default function () {
    const url = `${BASE_URL}/ping`;
    const params = {
        headers: {
            'Content-Type': 'text/plain',
        },
        timeout: '120s',
    };

    const res = http.get(url, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'content-type is text/plain': (r) => (r.headers['Content-Type'] || '').toLowerCase().includes('text/plain'),
        'has PONG response': (r) => {
            try {
                const response = r.body;
                return typeof response === 'string' && response === 'PONG';
            } catch (e) {
                return false;
            }
        },
    });
}
