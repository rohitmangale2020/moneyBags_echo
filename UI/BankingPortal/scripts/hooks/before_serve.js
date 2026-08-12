/**
 * Adds a same-origin development proxy for API Gateway requests.
 *
 * The browser calls the Oracle JET server on port 8000. Requests beginning
 * with /auth or /api are forwarded by this middleware to the existing API
 * Gateway, which defaults to http://localhost:8080.
 */
'use strict';

const http = require('http');
const https = require('https');

const DEFAULT_GATEWAY_URL = 'http://localhost:8080';
const PROXIED_PATH = /^\/(auth|api)(\/|$)/;

function createGatewayProxy(gatewayUrl) {
  const target = new URL(gatewayUrl);
  const transport = target.protocol === 'https:' ? https : http;

  return function gatewayProxy(request, response, next) {
    const requestPath = request.originalUrl || request.url;

    if (!PROXIED_PATH.test(requestPath)) {
      next();
      return;
    }

    const headers = Object.assign({}, request.headers, {
      host: target.host,
      'x-forwarded-host': request.headers.host,
      'x-forwarded-proto': request.socket.encrypted ? 'https' : 'http',
    });

    const proxyRequest = transport.request(
      {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port || (target.protocol === 'https:' ? 443 : 80),
        method: request.method,
        path: `${target.pathname.replace(/\/$/, '')}${requestPath}`,
        headers,
      },
        (proxyResponse) => {
          const responseHeaders = { ...proxyResponse.headers };

          // Prevent the browser from displaying its native HTTP Basic login dialog.
          delete responseHeaders['www-authenticate'];

          response.writeHead(proxyResponse.statusCode, responseHeaders);
          proxyResponse.pipe(response);
        },
    );

    proxyRequest.on('error', (error) => {
      if (response.headersSent) {
        response.destroy(error);
        return;
      }

      response.writeHead(502, { 'Content-Type': 'application/json' });
      response.end(
        JSON.stringify({
          message: `API Gateway is unavailable at ${target.origin}`,
        }),
      );
    });

    request.on('aborted', () => proxyRequest.destroy());
    request.pipe(proxyRequest);
  };
}

module.exports = function beforeServe(configObj) {
  const gatewayUrl = process.env.MONEYBAGS_GATEWAY_URL || DEFAULT_GATEWAY_URL;
  const existingMiddleware = configObj.preMiddleware || [];

  console.log(`Proxying /auth and /api requests to ${gatewayUrl}`);
  configObj.preMiddleware = [createGatewayProxy(gatewayUrl), ...existingMiddleware];

  return Promise.resolve(configObj);
};
