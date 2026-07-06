import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Newspaper, AlertTriangle, Filter, RefreshCw, Info, Calendar } from 'lucide-react'
import { Card, CardBody, Skeleton, EmptyState, Select, Button, Tag, Toggle, Input } from '@/shared/ui'
import { PageHeader } from '@/shared/ui/PageHeader'
import { usePrefersReducedMotion } from '@/shared/hooks/usePrefersReducedMotion'
import { useDatasetStore } from '@/shared/store/useDatasetStore'
import { AnomalyCardItem } from './components/AnomalyCardItem'
import { NewsCardItem } from './components/NewsCardItem'
import { useNewsStore } from './store/useNewsStore'

export function AiInsightsPage() {
    const reduced = usePrefersReducedMotion()
    const { subjects, loadFacts } = useDatasetStore()
    const {
        news,
        anomalies,
        loadingNews,
        loadingAnomalies,
        refreshing,
        error,
        fetchAnomalies,
        fetchNews,
        refreshAll,
        showAnomalies,
        toggleShowAnomalies,
    } = useNewsStore()

    const [selectedSubject, setSelectedSubject] = useState<string>('')
    const [periodDays, setPeriodDays] = useState<number>(90)
    const [threshold, setThreshold] = useState<number>(2.0)

    useEffect(() => {
        loadFacts()
    }, [])

    // ✅ Загружаем аномалии при монтировании и при смене фильтров
    useEffect(() => {
        fetchAnomalies(selectedSubject || undefined, threshold)
    }, [selectedSubject, threshold])

    // ✅ Новости загружаем отдельно
    useEffect(() => {
        fetchNews(selectedSubject || undefined, periodDays)
    }, [selectedSubject, periodDays])

    const handleRefresh = () => {
        refreshAll(selectedSubject || undefined, periodDays, threshold)
    }

    const motionProps = (i: number) =>
        reduced
            ? {}
            : {
                initial: { opacity: 0, y: 8 },
                animate: { opacity: 1, y: 0 },
                transition: { duration: 0.25, delay: i * 0.05 },
            }

    const anomaliesToShow = showAnomalies ? anomalies : []

    // ✅ Если есть кешированная ошибка, но данные есть – показываем данные
    const hasNews = news.length > 0
    const hasAnomalies = anomaliesToShow.length > 0
    const isNewsError = error && !hasNews
    const isAnomaliesError = error && !hasAnomalies

    return (
        <div>
            <PageHeader
                title="AI-аналитика"
                subtitle="Внешние новости и автоматически найденные аномалии"
                actions={
                    <Button variant="secondary" size="sm" onClick={handleRefresh} disabled={refreshing}>
                        <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
                        {refreshing ? 'Обновление...' : 'Обновить всё'}
                    </Button>
                }
            />

            <div className="mb-4 flex flex-wrap items-center gap-3">
                <div className="flex items-center gap-2">
                    <Filter className="h-4 w-4 text-text-muted" />
                    <span className="text-sm text-text-secondary">Фильтры:</span>
                </div>
                <Select
                    value={selectedSubject}
                    onChange={e => setSelectedSubject(e.target.value)}
                    className="w-48"
                >
                    <option value="">Все регионы</option>
                    {subjects.map(s => (
                        <option key={s} value={s}>{s}</option>
                    ))}
                </Select>

                {/* ✅ Период в днях с подсказкой */}
                <div className="flex items-center gap-2">
                    <div className="relative">
                        <Input
                            type="number"
                            min={1}
                            max={365}
                            value={periodDays}
                            onChange={e => setPeriodDays(Number(e.target.value) || 90)}
                            className="w-20 text-center"
                            aria-label="Количество дней для поиска новостей"
                        />
                        <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-text-muted">дн.</span>
                    </div>
                    <div className="group relative inline-block">
                        <Info className="h-4 w-4 cursor-help text-text-muted" />
                        <div className="invisible absolute bottom-full left-1/2 z-10 w-56 -translate-x-1/2 rounded-lg bg-surface-card p-2 text-xs text-text-secondary shadow-card group-hover:visible">
                            Количество дней в прошлое для поиска новостей. 90 дней ≈ квартал.
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    <span className="text-sm text-text-secondary">Порог аномалий (σ):</span>
                    <input
                        type="range"
                        min={1}
                        max={4}
                        step={0.1}
                        value={threshold}
                        onChange={e => setThreshold(Number(e.target.value))}
                        className="w-24 accent-accent-green"
                    />
                    <Tag tone="neutral">{threshold.toFixed(1)}</Tag>
                    <div className="group relative inline-block">
                        <Info className="h-4 w-4 cursor-help text-text-muted" />
                        <div className="invisible absolute bottom-full left-1/2 z-10 w-48 -translate-x-1/2 rounded-lg bg-surface-card p-2 text-xs text-text-secondary shadow-card group-hover:visible">
                            Отклонение больше указанного числа сигм считается аномалией.
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-2 ml-auto">
                    <Toggle
                        checked={showAnomalies}
                        onChange={toggleShowAnomalies}
                        label="Показывать аномалии"
                    />
                    {!showAnomalies && anomalies.length > 0 && (
                        <Tag tone="amber">скрыто {anomalies.length}</Tag>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                {/* Новости */}
                <div>
                    <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold tracking-wide text-text-primary">
                        <Newspaper className="h-4 w-4 text-text-secondary" />
                        Новости ({news.length})
                        {refreshing && <span className="ml-2 text-xs text-text-muted">(обновление...)</span>}
                        {error && !hasNews && <span className="ml-2 text-xs text-accent-red">(ошибка)</span>}
                    </h3>
                    {loadingNews && news.length === 0 ? (
                        <div className="space-y-3">
                            {Array.from({ length: 4 }).map((_, i) => (
                                <Skeleton key={i} className="h-32 w-full" />
                            ))}
                        </div>
                    ) : news.length ? (
                        <div className="space-y-3">
                            {news.map((n, i) => (
                                <motion.div key={n.id} {...motionProps(i)}>
                                    <NewsCardItem news={n} />
                                </motion.div>
                            ))}
                            {error && hasNews && (
                                <p className="text-xs text-accent-amber">{error}</p>
                            )}
                        </div>
                    ) : (
                        <Card>
                            <CardBody className="pt-5">
                                <EmptyState
                                    icon={<Newspaper className="h-8 w-8" />}
                                    title="Новостей нет"
                                    description={error || "По выбранным фильтрам новостей не найдено"}
                                />
                            </CardBody>
                        </Card>
                    )}
                </div>

                {/* Аномалии */}
                <div>
                    <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold tracking-wide text-text-primary">
                        <AlertTriangle className="h-4 w-4 text-text-secondary" />
                        Аномалии ({anomaliesToShow.length})
                        {!showAnomalies && anomalies.length > 0 && (
                            <Tag tone="amber">показаны не все</Tag>
                        )}
                    </h3>
                    {loadingAnomalies && anomalies.length === 0 ? (
                        <div className="space-y-3">
                            {Array.from({ length: 4 }).map((_, i) => (
                                <Skeleton key={i} className="h-24 w-full" />
                            ))}
                        </div>
                    ) : anomaliesToShow.length ? (
                        <div className="space-y-3">
                            {anomaliesToShow.map((a, i) => (
                                <motion.div key={a.id} {...motionProps(i)}>
                                    <AnomalyCardItem anomaly={a} />
                                </motion.div>
                            ))}
                        </div>
                    ) : (
                        <Card>
                            <CardBody className="pt-5">
                                <EmptyState
                                    icon={<AlertTriangle className="h-8 w-8" />}
                                    title={showAnomalies ? "Аномалий не найдено" : "Аномалии скрыты"}
                                    description={showAnomalies ? `При пороге ${threshold}σ отклонений не обнаружено` : "Включите показ аномалий"}
                                />
                            </CardBody>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    )
}