import http from 'http';
import https from 'https';
import axios from 'axios';

// High-performance HTTP/HTTPS agent with persistent TCP/TLS Keep-Alive
export const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 50,
  maxFreeSockets: 10,
});

export const httpsAgent = new https.Agent({
  keepAlive: true,
  keepAliveMsecs: 30000,
  maxSockets: 50,
  maxFreeSockets: 10,
});

// Configure default global axios instance to use Keep-Alive agents
axios.defaults.httpAgent = httpAgent;
axios.defaults.httpsAgent = httpsAgent;
