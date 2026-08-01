import axios from 'axios';
import { CONFIG } from '../config/constants';

export interface AniListMedia {
  id: number;
  title: {
    romaji: string;
    english: string;
    native: string;
  };
  format: string;
  episodes: number | null;
  status: string | null;
  bannerImage: string | null;
  coverImage: {
    extraLarge: string;
    large: string;
  };
  description: string;
  genres: string[];
  averageScore: number;
}

export interface EnrichedMetadata {
  synopsis: string | null;
  backdrop: string | null;
  image: string | null;
  score: number | null;
  year: number | null;
  episodeStills: Record<string, string>;
}

export class TMDBService {


  /**
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string): Promise<EnrichedMetadata | null> {
    if (!title) return null;

    // Generate candidate title queries to handle titles with colons, slashes, or franchise prefixes
    const candidates: string[] = [title];

    if (title.includes('//') || title.includes(':') || title.includes('-')) {
      const clean = title.replace(/\/\//g, ' ').replace(/:/g, ' ').replace(/\s+/g, ' ').trim();
      if (clean && !candidates.includes(clean)) candidates.push(clean);

      if (title.includes('//')) {
        const afterSlash = title.split('//').pop()?.trim();
        if (afterSlash && !candidates.includes(afterSlash)) candidates.push(afterSlash);
      }

      if (title.includes(':')) {
        const afterColon = title.split(':').pop()?.trim();
        if (afterColon && !candidates.includes(afterColon)) candidates.push(afterColon);
      }
    }

    for (const queryTitle of candidates) {
      try {
        const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(queryTitle)}`;
        const { data } = await axios.get(url, { timeout: 4000 });

        if (data) {
          const details = data.details || data.tmdb || {};
          const overview = details.overview || null;
          const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
          const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
          const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
          const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
          const episodeStills: Record<string, string> = data.episodeStills || {};

          if (backdropPath || overview) {
            return {
              synopsis: overview,
              backdrop: backdropPath,
              image: posterPath,
              score: voteAverage,
              year: year,
              episodeStills: episodeStills,
            };
          }
        }
      } catch {
        // Try next candidate
      }
    }

    return null;
  }

  /**
   * Fetch AniList media info (episodes count, genres, native title)
   */
  static async getAniListMetadata(title: string): Promise<Partial<AniListMedia> | null> {
    const query = `
      query ($search: String) {
        Media (search: $search, type: ANIME) {
          id
          format
          title {
            romaji
            english
            native
          }
          episodes
          status
          bannerImage
          coverImage {
            extraLarge
            large
          }
          description
          genres
          averageScore
        }
      }
    `;

    try {
      const response = await axios.post(
        CONFIG.ANILIST_API_URL,
        {
          query,
          variables: { search: title },
        },
        { timeout: 4000 }
      );

      return response.data?.data?.Media || null;
    } catch {
      return null;
    }
  }

  /**
   * Fetch Kitsu cover image banner
   */
  static async getKitsuMetadata(title: string): Promise<string | null> {
    try {
      const url = `https://kitsu.io/api/edge/anime?filter[text]=${encodeURIComponent(title)}`;
      const { data } = await axios.get(url, {
        headers: {
          'Accept': 'application/vnd.api+json',
          'Content-Type': 'application/vnd.api+json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        },
        timeout: 4000,
      });
      if (data && Array.isArray(data.data) && data.data.length > 0) {
        const coverImage = data.data[0].attributes?.coverImage;
        return coverImage?.original || coverImage?.large || coverImage?.small || null;
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Translate English text to Spanish using Google Translate API
   */
  static async translateToSpanish(text: string): Promise<string | null> {
    if (!text) return null;
    try {
      const cleanText = text.replace(/<[^>]*>?/gm, '').trim();
      if (!cleanText) return null;
      const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t&q=${encodeURIComponent(cleanText)}`;
      const res = await axios.get(url, { timeout: 4000 });
      if (res.data && res.data[0]) {
        const translated = res.data[0].map((item: any) => item[0]).join('');
        if (translated && translated.trim().length > 0) {
          return translated.trim();
        }
      }
    } catch {
      // ignore
    }
    return null;
  }
}
