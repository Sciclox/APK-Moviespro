import { URL } from 'node:url';

const DEFAULT_ALLOWED_HOSTS = [
  'jkanime.net',
  'jkdesa.com',
  'jkplayers.com',
  'ducvomes.com',
  'playmudos.com',
  'playmudos.net',
  'yourupload.com',
  'mp4upload.com',
  'filemoon.sx',
  'ok.ru',
  'monoschinos2.com',
  'rumble.cloud',
  '1a-1791.com',
  'zonaaps.com',
  'zonaaps-player.xyz',
  'moviedays.top',
  'moviedays.lat',
  '11231data.com',
  'akamaized.net',
];

function configuredHosts(): string[] {
  const fromEnvironment = process.env.PROXY_ALLOWED_HOSTS
    ?.split(',')
    .map((host) => host.trim().toLowerCase())
    .filter(Boolean);
  return fromEnvironment?.length ? fromEnvironment : DEFAULT_ALLOWED_HOSTS;
}

function hostMatches(hostname: string, allowedHost: string): boolean {
  return hostname === allowedHost || hostname.endsWith(`.${allowedHost}`);
}

/**
 * Validates every upstream URL accepted by the streaming proxy.
 * The proxy must never be usable to request local/private infrastructure.
 */
export function validateProxyUrl(rawUrl: string): URL {
  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error('La URL del recurso no es válida');
  }

  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('El protocolo del recurso no está permitido');
  }

  const hostname = parsed.hostname.toLowerCase();
  if (!configuredHosts().some((allowedHost) => hostMatches(hostname, allowedHost))) {
    throw new Error('El proveedor de video no está permitido');
  }

  return parsed;
}
