import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';
import axios from 'axios';
import * as cheerio from 'cheerio';

const router = Router();

/**
 * @route GET /api/v1/health
 * @desc Health check
 */
router.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'APK-AnimeTV Backend Gateway',
  });
});

/**
 * @route GET /api/v1/anime/hero
 * @desc Get featured anime banners for TV home screen
 */
router.get('/anime/hero', async (req: Request, res: Response) => {
  try {
    const tipo = req.query.tipo as string;
    const banners = await ScraperService.getHeroBanners(tipo);
    res.json({ success: true, data: banners });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest
 * @desc Get latest currently releasing anime (Animes en emisión)
 */
router.get('/anime/latest', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatest();
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/latest-episodes
 * @desc Get recently uploaded episodes from JKAnime home page
 */
router.get('/anime/latest-episodes', async (_req: Request, res: Response) => {
  try {
    const results = await ScraperService.getLatestEpisodes();
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/search
 * @desc Search anime by query string
 */
router.get('/anime/search', async (req: Request, res: Response) => {
  try {
    const query = req.query.q as string;
    if (!query) {
      return res.status(400).json({ success: false, error: 'Query parameter "q" is required' });
    }
    const results = await ScraperService.searchAnime(query);
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/catalog
 * @desc Get anime catalog filtered by genre
 */
router.get('/anime/catalog', async (req: Request, res: Response) => {
  try {
    const genre = req.query.genre as string;
    const categoria = req.query.categoria as string;
    const tipo = req.query.tipo as string;
    const estado = req.query.estado as string;
    const demografia = req.query.demografia as string;
    const filtro = req.query.filtro as string;
    const page = parseInt(req.query.page as string || '1', 10);
    const results = await ScraperService.getCatalog({ genre, categoria, tipo, estado, demografia, filtro, page });
    res.json({ success: true, data: results });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/hub/:category
 * @desc Get unified category hub screen data (Latino, Películas, Donghua)
 */
router.get('/anime/hub/:category', async (req: Request, res: Response) => {
  try {
    const category = req.params.category as string;
    const hub = await ScraperService.getCategoryHub(category);
    res.json({ success: true, data: hub });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/anime/details/:slug
 * @desc Get anime details, metadata, and episode list
 */
router.get('/anime/details/:slug', async (req: Request, res: Response) => {
  try {
    const slug = req.params.slug as string;
    const details = await ScraperService.getAnimeDetails(slug);
    res.json({ success: true, data: details });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

function getBackendBase(req: Request): string {
  const host = (req.headers['x-forwarded-host'] as string) || req.get('host') || 'apk-animetv-backend.onrender.com';
  if (host.includes('onrender.com') || !host.includes('localhost')) {
    return `https://${host}`;
  }
  const proto = (req.headers['x-forwarded-proto'] as string) || req.protocol || 'https';
  return `${proto}://${host}`;
}

/**
 * @route GET /api/v1/anime/watch/*
 * @desc Get episode streaming sources for ExoPlayer
 */
router.get('/anime/watch/*', async (req: Request, res: Response) => {
  const startTime = Date.now();
  try {
    const episodeSlug = Array.isArray(req.params[0]) ? req.params[0].join('/') : (req.params[0] || '');
    const backendBase = getBackendBase(req);
    const stream = await ScraperService.getStreamSources(episodeSlug, backendBase);
    console.log(`[Telemetry /watch] ⏱️ Resolved ${episodeSlug} in ${Date.now() - startTime} ms`);
    res.json({ success: true, data: stream });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

function extractFullTargetUrl(req: Request): string | null {
  const rawIndex = req.originalUrl.indexOf('url=');
  if (rawIndex === -1) return (req.query.url as string) || null;
  const rawParam = req.originalUrl.substring(rawIndex + 4);
  try {
    return decodeURIComponent(rawParam);
  } catch {
    return rawParam;
  }
}

interface CacheEntry<T> {
  data: T;
  expiresAt: number;
}

class SimpleMemoryCache<T> {
  private cache = new Map<string, CacheEntry<T>>();
  private maxEntries = 200;

  get(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }
    return entry.data;
  }

  set(key: string, data: T, ttlMs: number): void {
    if (this.cache.size >= this.maxEntries) {
      const oldestKey = this.cache.keys().next().value;
      if (oldestKey) this.cache.delete(oldestKey);
    }
    this.cache.set(key, { data, expiresAt: Date.now() + ttlMs });
  }
}

const m3u8Cache = new SimpleMemoryCache<string>();
const inFlightM3u8 = new Map<string, Promise<string>>();

/**
 * @route GET /api/v1/proxy/m3u8
 * @desc Proxy HLS manifest with 30s normalized memory cache and request coalescing
 */
router.get('/proxy/m3u8', async (req: Request, res: Response) => {
  const t_received = Date.now();
  try {
    const rawUrl = extractFullTargetUrl(req);
    if (!rawUrl) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    // Normalize URL key to prevent duplicate entries
    let cacheKey: string;
    try {
      const parsedUrl = new URL(rawUrl);
      parsedUrl.searchParams.sort();
      cacheKey = `m3u8_${parsedUrl.toString()}`;
    } catch {
      cacheKey = `m3u8_${rawUrl.trim()}`;
    }

    // 1. Check Memory Cache Hit
    const cachedManifest = m3u8Cache.get(cacheKey);
    if (cachedManifest) {
      res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Cache-Control', 'public, max-age=60');
      const hitDuration = Date.now() - t_received;
      console.log(`[Telemetry /m3u8 CACHE HIT] ⚡ Responded in ${hitDuration} ms (Saved ~1,100ms upstream)`);
      res.send(cachedManifest);
      return;
    }

    // 2. Request Coalescing (Deduplication)
    let fetchPromise = inFlightM3u8.get(cacheKey);
    if (!fetchPromise) {
      fetchPromise = (async () => {
        const t_upstream_start = Date.now();
        const { data: manifest } = await axios.get(rawUrl, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Referer': 'https://jkanime.net/',
            'Origin': 'https://jkanime.net',
          },
          timeout: 8000,
          responseType: 'text',
        });
        const upstreamDuration = Date.now() - t_upstream_start;

        const t_rewrite_start = Date.now();
        const backendBase = getBackendBase(req);
        const baseUrlObj = new URL(rawUrl);
        const lines = manifest.split('\n');
        const rewrittenLines = lines.map((line: string) => {
          const trimmed = line.trim();
          if (!trimmed || trimmed.startsWith('#')) {
            return line;
          }
          let fullSegmentUrl = trimmed;
          if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) {
            fullSegmentUrl = new URL(trimmed, baseUrlObj.href).href;
          }
          return fullSegmentUrl;
        });

        const fixed = rewrittenLines.join('\n');
        const rewriteDuration = Date.now() - t_rewrite_start;

        // Cache manifest in memory for 30 seconds
        m3u8Cache.set(cacheKey, fixed, 30_000);
        console.log(`[Telemetry /m3u8 CACHE MISS] ⏱️ Upstream CDN: ${upstreamDuration}ms | String Rewrite: ${rewriteDuration}ms`);
        return fixed;
      })().finally(() => {
        inFlightM3u8.delete(cacheKey);
      });

      inFlightM3u8.set(cacheKey, fetchPromise);
    }

    const fixed = await fetchPromise;

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'public, max-age=60');
    console.log(`[Telemetry /m3u8 TOTAL] ⏱️ Sent response in ${Date.now() - t_received} ms`);
    res.send(fixed);
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/proxy/ts
 * @desc Proxy TS video segments with host and upstream TTFB breakdown
 */
router.get('/proxy/ts', async (req: Request, res: Response) => {
  const t_received = Date.now();
  const controller = new AbortController();

  req.on('close', () => {
    if (!res.writableEnded) {
      controller.abort();
    }
  });

  try {
    const url = extractFullTargetUrl(req);
    if (!url) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const t_upstream_start = Date.now();
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://jkanime.net/',
        'Origin': 'https://jkanime.net',
      },
      timeout: 8000,
      responseType: 'stream',
      signal: controller.signal,
    });

    const t_upstream_ttfb = Date.now() - t_upstream_start;
    const contentType = response.headers['content-type'];
    const contentLength = response.headers['content-length'];

    res.setHeader('Content-Type', typeof contentType === 'string' ? contentType : 'video/mp2t');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    if (contentLength) {
      res.setHeader('Content-Length', String(contentLength));
    }

    const segmentHost = new URL(url).hostname;
    console.log(`[Telemetry /ts BREAKDOWN] ⚡ Host: ${segmentHost} | Upstream CDN TTFB: ${t_upstream_ttfb}ms | Express Total TTFB: ${Date.now() - t_received}ms`);

    response.data.on('error', (err: any) => {
      if (!res.headersSent) {
        res.status(502).json({ success: false, error: 'Stream error' });
      } else {
        res.destroy();
      }
    });

    response.data.pipe(res);
  } catch (error: any) {
    if (!res.headersSent) {
      res.status(502).json({ success: false, error: 'Segment fetch failed' });
    }
  }
});

interface CachedStream {
  directUrl: string;
  refererHeader: string;
  timestamp: number;
}
const resolvedStreamCache = new Map<string, CachedStream>();

/**
 * @route GET /api/v1/proxy/video
 * @desc Stream direct MP4/HLS video bytes directly to ExoPlayer/Browser (Tokianime architecture)
 */
router.get('/proxy/video', async (req: Request, res: Response) => {
  try {
    const embedUrl = extractFullTargetUrl(req);
    if (!embedUrl) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const DEFAULT_HEADERS = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    };

    let directUrl: string | null = null;
    let refererHeader = 'https://google.com/';

    // Helper to resolve direct stream URL from embed
    const getOrResolveDirectUrl = async (forceRefresh = false): Promise<{ directUrl: string; refererHeader: string } | null> => {
      if (forceRefresh) {
        resolvedStreamCache.delete(embedUrl);
      }
      
      const cached = resolvedStreamCache.get(embedUrl);
      if (cached && (Date.now() - cached.timestamp < 30 * 60 * 1000) && !forceRefresh) {
        // LRU pattern: move to end
        resolvedStreamCache.delete(embedUrl);
        resolvedStreamCache.set(embedUrl, cached);
        return { directUrl: cached.directUrl, refererHeader: cached.refererHeader };
      }

      let extractedUrl: string | null = null;
      let referer = 'https://google.com/';

      // 1. YourUpload
      if (embedUrl.includes('yourupload')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const m = html.match(/jwplayer\("[^"]+"\)\.setup\(\{\s*file:\s*["']([^"']+\.mp4[^"']*)["']/i) || html.match(/(https?:\/\/[^\s"'<>]+\.mp4[^\s"'<>]*)/i);
          if (m && m[1]) {
            extractedUrl = m[1];
            referer = 'https://www.yourupload.com/';
          }
        } catch {}
      }

      // 2. Mp4Upload
      if (!extractedUrl && embedUrl.includes('mp4upload')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const m = html.match(/src:\s*["'](https?:\/\/[^"']+\.mp4[^"']*)["']/i) || html.match(/(https?:\/\/[^\s"'<>]+\.mp4[^\s"'<>]*)/i);
          if (m && m[1]) {
            extractedUrl = m[1];
            referer = 'https://www.mp4upload.com/';
          }
        } catch {}
      }

      // 3. Filemoon
      if (!extractedUrl && embedUrl.includes('filemoon')) {
        try {
          const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const packedMatch = html.match(/eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.+?)',(\d+),(\d+),'(.+?)'\.split\('\|'\)/s);
          if (packedMatch) {
            const p = packedMatch[1];
            const a = parseInt(packedMatch[2], 10);
            const c = parseInt(packedMatch[3], 10);
            const k = packedMatch[4].split('|');
            let currentC = c;
            let result = p;
            while (currentC--) {
              if (k[currentC]) {
                result = result.replace(new RegExp('\\b' + currentC.toString(a) + '\\b', 'g'), k[currentC]);
              }
            }
            const m3u8 = result.match(/(https?:\/\/[^\s"'<>]+\.m3u8[^\s"'<>]*)/i);
            if (m3u8 && m3u8[1]) {
              extractedUrl = m3u8[1];
              referer = 'https://filemoon.sx/';
            }
          }
        } catch {}
      }

      // 4. OK.ru
      if (!extractedUrl && embedUrl.includes('ok.ru')) {
        try {
          const secureUrl = embedUrl.replace('http://', 'https://');
          const { data: html } = await axios.get(secureUrl, { headers: DEFAULT_HEADERS, timeout: 4000 });
          const optionsAttr = cheerio.load(html)('div[data-options]').attr('data-options');
          if (optionsAttr) {
            const decoded = optionsAttr.replace(/&quot;/g, '"').replace(/&amp;/g, '&');
            const json = JSON.parse(decoded);
            const flashvars = JSON.parse(json.flashvars.metadata);
            if (flashvars.videos && flashvars.videos.length > 0) {
              const preferred = flashvars.videos.find((v: any) => v.name === 'hd') || flashvars.videos.find((v: any) => v.name === 'sd') || flashvars.videos[0];
              if (preferred && preferred.url) {
                extractedUrl = preferred.url;
                referer = 'https://ok.ru/';
              }
            }
          }
        } catch {}
      }

      if (!extractedUrl) {
        if (embedUrl.includes('.mp4') || embedUrl.includes('.m3u8')) {
          extractedUrl = embedUrl;
        }
      }

      if (extractedUrl) {
        if (resolvedStreamCache.size >= 200 && !resolvedStreamCache.has(embedUrl)) {
          const firstKey = resolvedStreamCache.keys().next().value;
          if (firstKey) {
            resolvedStreamCache.delete(firstKey);
          }
        }
        resolvedStreamCache.set(embedUrl, {
          directUrl: extractedUrl,
          refererHeader: referer,
          timestamp: Date.now(),
        });
        return { directUrl: extractedUrl, refererHeader: referer };
      }

      return null;
    };

    let resolved = await getOrResolveDirectUrl(false);
    if (!resolved) {
      res.redirect(302, embedUrl);
      return;
    }

    console.log(`[Telemetry /proxy/video] 🔀 Redirecting 302 directly to CDN: ${resolved.directUrl}`);
    res.redirect(302, resolved.directUrl);
  } catch (error: any) {
    if (!res.headersSent) {
      res.status(502).json({ success: false, error: 'Video stream resolution failed' });
    }
  }
});

export default router;
