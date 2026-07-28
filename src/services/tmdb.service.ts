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
   * Translate English text to Spanish using MyMemory API
   */
  static async translateToSpanish(text: string): Promise<string | null> {
    if (!text || text.trim().length === 0) return null;
    const cleanText = text.replace(/<[^>]*>?/gm, '').trim();
    if (cleanText.length === 0) return null;

    try {
      const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(cleanText.substring(0, 400))}&langpair=en|es`;
      const { data } = await axios.get(url, { timeout: 3500 });
      if (data && data.responseData && data.responseData.translatedText) {
        const translated = data.responseData.translatedText;
        if (translated && !translated.startsWith('QUERY LENGTH LIMIT')) {
          return translated;
        }
      }
    } catch {
      // ignore
    }
    return null;
  }

  /**
   * Fetch metadata enriched with 100% Spanish synopsis & episode stills from TMDB / AnimeVerse
   */
  static async getSpanishMetadata(title: string): Promise<EnrichedMetadata | null> {
    try {
      const url = `${CONFIG.ANIMEVERSE_BASE_URL}/api/v1/anime/enrich?title=${encodeURIComponent(title)}`;
      const { data } = await axios.get(url, { timeout: 5000 });

      if (data) {
        const details = data.details || data.tmdb || {};
        const overview = details.overview || null;
        const backdropPath = details.backdrop_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.backdrop_path}` : null;
        const posterPath = details.poster_path ? `${CONFIG.TMDB_IMAGE_BASE}${details.poster_path}` : null;
        const voteAverage = details.vote_average ? Math.round(details.vote_average * 10) / 10 : null;
        const year = details.first_air_date ? parseInt(details.first_air_date.split('-')[0], 10) : null;
        const episodeStills: Record<string, string> = data.episodeStills || {};

        return {
          synopsis: overview,
          backdrop: backdropPath,
          image: posterPath,
          score: voteAverage,
          year: year,
          episodeStills: episodeStills,
        };
      }
    } catch {
      // ignore
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
}
