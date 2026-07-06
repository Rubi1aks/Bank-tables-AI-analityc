import { create } from 'zustand'
import { api } from '@/shared/lib/api'
import type { AnomalyCard, NewsCard } from '@/shared/lib/api-types'

interface NewsState {
    news: NewsCard[]
    anomalies: AnomalyCard[]
    loadingNews: boolean
    loadingAnomalies: boolean
    refreshing: boolean
    error: string | null
    showAnomalies: boolean
    lastFetchedNews: number | null
    lastFetchedAnomalies: number | null

    fetchNews: (subject?: string, period?: number, force?: boolean) => Promise<void>
    fetchAnomalies: (subject?: string, threshold?: number, force?: boolean) => Promise<void>
    refreshAll: (subject?: string, period?: number, threshold?: number) => Promise<void>
    toggleShowAnomalies: () => void
    clear: () => void
}

const CACHE_TTL = 5 * 60 * 1000 // 5 минут

export const useNewsStore = create<NewsState>((set, get) => ({
    news: [],
    anomalies: [],
    loadingNews: false,
    loadingAnomalies: false,
    refreshing: false,
    error: null,
    showAnomalies: true,
    lastFetchedNews: null,
    lastFetchedAnomalies: null,

    fetchNews: async (subject, period = 90, force = false) => {
        const { lastFetchedNews, news } = get()
        const cacheKey = `${subject || 'all'}|${period}`
        const cached = sessionStorage.getItem(`news_${cacheKey}`)

        // Если есть кеш и не принудительно, используем его
        if (!force && lastFetchedNews && Date.now() - lastFetchedNews < CACHE_TTL && news.length > 0) {
            return
        }

        set({ loadingNews: true, error: null })
        try {
            const newsData = await api.getNews(subject, period)
            // Сохраняем в sessionStorage
            sessionStorage.setItem(`news_${cacheKey}`, JSON.stringify(newsData))
            set({
                news: newsData,
                loadingNews: false,
                lastFetchedNews: Date.now(),
                error: null,
            })
        } catch (e) {
            // При ошибке пробуем достать из кеша
            const cachedData = sessionStorage.getItem(`news_${cacheKey}`)
            if (cachedData) {
                try {
                    const parsed = JSON.parse(cachedData)
                    set({ news: parsed, loadingNews: false, error: 'Использован кеш (сервер временно недоступен)' })
                    return
                } catch { }
            }
            set({
                loadingNews: false,
                error: e instanceof Error ? e.message : 'Ошибка загрузки новостей',
            })
        }
    },

    fetchAnomalies: async (subject, threshold = 2.0, force = false) => {
        const { lastFetchedAnomalies, anomalies } = get()
        const cacheKey = `${subject || 'all'}|${threshold}`
        const cached = sessionStorage.getItem(`anomalies_${cacheKey}`)

        if (!force && lastFetchedAnomalies && Date.now() - lastFetchedAnomalies < CACHE_TTL && anomalies.length > 0) {
            return
        }

        set({ loadingAnomalies: true, error: null })
        try {
            const anomaliesData = await api.getAnomalies(subject, undefined, threshold)
            sessionStorage.setItem(`anomalies_${cacheKey}`, JSON.stringify(anomaliesData))
            set({
                anomalies: anomaliesData,
                loadingAnomalies: false,
                lastFetchedAnomalies: Date.now(),
                error: null,
            })
        } catch (e) {
            const cachedData = sessionStorage.getItem(`anomalies_${cacheKey}`)
            if (cachedData) {
                try {
                    const parsed = JSON.parse(cachedData)
                    set({ anomalies: parsed, loadingAnomalies: false })
                    return
                } catch { }
            }
            set({
                loadingAnomalies: false,
                error: e instanceof Error ? e.message : 'Ошибка загрузки аномалий',
            })
        }
    },

    refreshAll: async (subject, period = 90, threshold = 2.0) => {
        set({ refreshing: true, error: null })
        try {
            const [newsData, anomaliesData] = await Promise.all([
                api.getNews(subject, period),
                api.getAnomalies(subject, undefined, threshold),
            ])
            const cacheKeyNews = `${subject || 'all'}|${period}`
            const cacheKeyAnom = `${subject || 'all'}|${threshold}`
            sessionStorage.setItem(`news_${cacheKeyNews}`, JSON.stringify(newsData))
            sessionStorage.setItem(`anomalies_${cacheKeyAnom}`, JSON.stringify(anomaliesData))
            set({
                news: newsData,
                anomalies: anomaliesData,
                refreshing: false,
                lastFetchedNews: Date.now(),
                lastFetchedAnomalies: Date.now(),
                error: null,
            })
        } catch (e) {
            set({ refreshing: false, error: e instanceof Error ? e.message : 'Ошибка обновления' })
        }
    },

    toggleShowAnomalies: () => set((s) => ({ showAnomalies: !s.showAnomalies })),

    clear: () => set({
        news: [],
        anomalies: [],
        loadingNews: false,
        loadingAnomalies: false,
        refreshing: false,
        error: null,
        showAnomalies: true,
        lastFetchedNews: null,
        lastFetchedAnomalies: null,
    }),
}))