import http from 'k6/http';
import {Counter} from 'k6/metrics';
import {textSummary} from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export const http_200_reqs = new Counter('http_200_reqs');

export const options = {
    thresholds: {
        //http_req_failed: ['rate==0'],
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

    // Count only successful 200 responses; 429s (and others) are ignored
    if (res.status === 200) {
        http_200_reqs.add(1);
    }
}

// Ensure the custom metric exists even when there are zero 200 responses
export function teardown() {
    http_200_reqs.add(0);
}

// Print 200-OK requests per second at the end of the test
export function handleSummary(data) {
    const count = data.metrics?.http_200_reqs?.values?.count ?? 0;
    const secs = (data.state?.testRunDurationMs ?? 0) / 1000;
    const rps = secs > 0 ? (count / secs) : 0;
    return {
        stdout:
            textSummary(data, {indent: ' ', enableColors: true}) +
            `\nhttp_200_reqs RPS: ${rps.toFixed(2)} req/s (count=${count}, duration=${secs.toFixed(2)}s)\n`,
    };
}
