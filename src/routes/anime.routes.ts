import { Router, Request, Response } from 'express';
import { ScraperService } from '../services/scraper.service';

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
router.get('/anime/hero', async (_req: Request, res: Response) => {
  try {
    const banners = await ScraperService.getHeroBanners();
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
    const results = await ScraperService.getLatestReleasing();
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
    const page = parseInt(req.query.page as string || '1', 10);
    const results = await ScraperService.getCatalog(genre, page);
    res.json({ success: true, data: results });
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

/**
 * @route GET /api/v1/anime/watch/*
 * @desc Get episode streaming sources for ExoPlayer
 */
router.get('/anime/watch/*', async (req: Request, res: Response) => {
  try {
    const episodeSlug = Array.isArray(req.params[0]) ? req.params[0].join('/') : (req.params[0] || '');
    const protocol = req.headers['x-forwarded-proto'] || req.protocol;
    const host = req.headers['x-forwarded-host'] || req.get('host');
    const backendBase = `${protocol}://${host}`;
    const stream = await ScraperService.getStreamSources(episodeSlug, backendBase);
    res.json({ success: true, data: stream });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/proxy/m3u8
 * @desc Proxy HLS manifest and rewrite dead CDN domains to working ones
 */
router.get('/proxy/m3u8', async (req: Request, res: Response) => {
  try {
    const url = req.query.url as string;
    if (!url) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const { data: manifest } = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://jkanime.net/',
        'Origin': 'https://jkanime.net',
      },
      timeout: 8000,
      responseType: 'text',
    });

    // Smart CDN rewrite: test if cdn1 is responsive. If online, keep fast original CDNs. If offline/timing out, apply fallback to cdn3/cdn4.
    let fixed = manifest;
    if (manifest.includes('cdn1.ducvomes.com') || manifest.includes('cdn2.ducvomes.com')) {
      try {
        await axios.head('https://cdn1.ducvomes.com/', {
          timeout: 1500,
          validateStatus: (status) => status < 500,
        });
      } catch {
        fixed = manifest
          .replace(/https?:\/\/cdn1\.ducvomes\.com/g, 'https://cdn3.ducvomes.com')
          .replace(/https?:\/\/cdn2\.ducvomes\.com/g, 'https://cdn4.ducvomes.com');
      }
    }

    res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.send(fixed);
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * @route GET /api/v1/proxy/ts
 * @desc Proxy TS video segments with required headers
 */
router.get('/proxy/ts', async (req: Request, res: Response) => {
  try {
    const url = req.query.url as string;
    if (!url) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const response = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://jkanime.net/',
        'Origin': 'https://jkanime.net',
      },
      timeout: 15000,
      responseType: 'arraybuffer',
    });

    res.setHeader('Content-Type', 'video/mp2t');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.send(Buffer.from(response.data));
  } catch (error: any) {
    res.status(502).json({ success: false, error: 'Segment fetch failed' });
  }
});

/**
 * @route GET /api/v1/proxy/stream
 * @desc Dynamically resolve embed page URL to direct video stream (.m3u8 / .mp4) and redirect ExoPlayer
 */
router.get('/proxy/stream', async (req: Request, res: Response) => {
  try {
    const embedUrl = req.query.url as string;
    if (!embedUrl) {
      res.status(400).json({ success: false, error: 'Missing url parameter' });
      return;
    }

    const axios = (await import('axios')).default;
    const cheerio = (await import('cheerio')).default;
    const DEFAULT_HEADERS = {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    };

    // 1. Filemoon unpacking
    if (embedUrl.includes('filemoon')) {
      try {
        const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 5000 });
        const packedMatch = html.match(/eval\(function\(p,a,c,k,e,d\)\{.*\}\('(.+?)',(\d+),(\d+),'(.+?)'\.split\('\|'\)/s);
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
            res.redirect(302, m3u8[1]);
            return;
          }
        }
      } catch {
        // ignore
      }
    }

    // 2. OK.ru metadata
    if (embedUrl.includes('ok.ru')) {
      try {
        const secureUrl = embedUrl.replace('http://', 'https://');
        const { data: html } = await axios.get(secureUrl, { headers: DEFAULT_HEADERS, timeout: 5000 });
        const optionsAttr = cheerio.load(html)('div[data-options]').attr('data-options');
        if (optionsAttr) {
          const decoded = optionsAttr.replace(/&quot;/g, '"').replace(/&amp;/g, '&');
          const json = JSON.parse(decoded);
          const flashvars = JSON.parse(json.flashvars.metadata);
          if (flashvars.videos && flashvars.videos.length > 0) {
            const preferred = flashvars.videos.find((v: any) => v.name === 'hd') || flashvars.videos.find((v: any) => v.name === 'sd') || flashvars.videos[0];
            if (preferred && preferred.url) {
              res.redirect(302, preferred.url);
              return;
            }
          }
        }
      } catch {
        // ignore
      }
    }

    // 3. Fallback direct match for mp4/m3u8 in embed HTML
    try {
      const { data: html } = await axios.get(embedUrl, { headers: DEFAULT_HEADERS, timeout: 5000 });
      const directMatch = html.match(/(https?:\/\/[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)/i);
      if (directMatch && directMatch[1]) {
        res.redirect(302, directMatch[1]);
        return;
      }
    } catch {
      // ignore
    }

    // Fallback redirect to original embed URL
    res.redirect(302, embedUrl);
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

export default router;
